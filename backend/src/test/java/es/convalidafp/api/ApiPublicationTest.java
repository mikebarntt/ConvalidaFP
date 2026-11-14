package es.convalidafp.api;

import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import es.convalidafp.catalog.DatasetPublicationService;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.ingestion.DatasetImportService;
import es.convalidafp.ingestion.ImportResult;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.testsupport.SampleFiles;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * Publicar o restaurar una versión cambia lo que responde la API: la caché se vacía con el evento de
 * publicación y el ETag cambia. Una simulación ya iniciada sigue con la versión que resolvió.
 *
 * <p>Estos tests publican versiones de verdad, así que usan su propio contexto y su propia base de
 * datos. Cada uno deja publicada la versión original al terminar.
 */
@SpringBootTest(
    properties = {
      "convalidafp.bootstrap.sample-dataset.enabled=true",
      "convalidafp.bootstrap.sample-dataset.dir=${convalidafp.dataDir:../data}/sample",
      "convalidafp.api.allowed-origins=https://spa.example,https://otra-spa.example",
      "test.context=api-publication"
    })
@Import(TestcontainersConfiguration.class)
class ApiPublicationTest extends ApiTestSupport {

  private static final String SIMULATION = "/api/v1/simulations?cycle=CF-PR&degree=UPR-GD";

  @Autowired private DatasetImportService importer;
  @Autowired private DatasetPublicationService publication;
  @Autowired private DatasetVersionRepository versions;
  @Autowired private PublishedDatasets datasets;
  @Autowired private SimulationService simulations;

  @Test
  @DisplayName("Publicar una versión nueva cambia la respuesta y el ETag; restaurar la anterior, otra vez")
  void publishingChangesTheResponseAndTheEtag() throws Exception {
    Long originalId = currentVersionId();
    MockHttpServletResponse before = perform(SIMULATION).getResponse();
    String etagBefore = before.getHeader("ETag");
    assertThat(body(before).get("recognizedEcts").decimalValue()).isEqualByComparingTo("30");
    assertThat(notModified(etagBefore)).isTrue();

    // Se publica una corrección del mismo curso: el tope de la tabla baja de 30 a 24.
    Long correctedId = importCorrection();
    publication.publish(correctedId);

    MockHttpServletResponse after = perform(SIMULATION).getResponse();
    String etagAfter = after.getHeader("ETag");
    JsonNode afterBody = body(after);
    assertThat(afterBody.get("recognizedEcts").decimalValue()).isEqualByComparingTo("24");
    assertThat(afterBody.get("dataset").get("versionId").asLong()).isEqualTo(correctedId);
    assertThat(etagAfter).isNotEqualTo(etagBefore);
    // Quien tenía la respuesta antigua en caché recibe la nueva, no un 304.
    assertThat(notModified(etagBefore)).isFalse();
    assertThat(notModified(etagAfter)).isTrue();
    // El resto de endpoints también cambian de versión.
    assertThat(perform("/api/v1/cycles").getResponse().getHeader("ETag")).isEqualTo(etagAfter);
    assertThat(getJson("/api/v1/dataset").get("versionId").asLong()).isEqualTo(correctedId);
    // El otro curso no cambia de versión.
    assertThat(getJson("/api/v1/dataset?year=2025-26").get("versionId").asLong()).isNotEqualTo(correctedId);

    // Restaurar la versión archivada también vacía la caché y cambia el ETag.
    publication.publish(originalId);

    MockHttpServletResponse restored = perform(SIMULATION).getResponse();
    assertThat(body(restored).get("recognizedEcts").decimalValue()).isEqualByComparingTo("30");
    assertThat(body(restored).get("dataset").get("versionId").asLong()).isEqualTo(originalId);
    assertThat(restored.getHeader("ETag")).isNotEqualTo(etagAfter).isNotEqualTo(etagBefore);
    assertThat(versions.findById(correctedId).orElseThrow().getStatus()).isEqualTo(Status.ARCHIVADO);
  }

  @Test
  @DisplayName("Caso 14: una simulación usa una única versión aunque se publique otra mientras tanto")
  void simulationKeepsItsVersionWhileAnotherIsPublished() {
    Long originalId = currentVersionId();
    // Una petición resuelve la versión vigente una sola vez...
    PublishedDataset pinned = datasets.resolve(null);
    assertThat(pinned.info().versionId()).isEqualTo(originalId);

    // ...y antes de que termine se publica otra versión del mismo curso.
    Long correctedId = importCorrection();
    publication.publish(correctedId);

    // La petición en curso sigue calculando con la versión que resolvió, de principio a fin.
    Recognized inFlight = (Recognized) simulations.simulate(pinned, "CF-PR", "UPR-GD");
    assertThat(inFlight.recognizedEcts()).isEqualByComparingTo("30");
    assertThat(inFlight.input().datasetVersionId()).isEqualTo(originalId);
    assertThat(pinned.table("CF-PR", "UPR-GD").orElseThrow().tableMaxEcts()).isEqualByComparingTo("30");

    // Las peticiones nuevas reciben ya la versión publicada, también entera.
    PublishedDataset fresh = datasets.resolve(null);
    assertThat(fresh).isNotSameAs(pinned);
    assertThat(fresh.info().versionId()).isEqualTo(correctedId);
    Recognized next = (Recognized) simulations.simulate(fresh, "CF-PR", "UPR-GD");
    assertThat(next.recognizedEcts()).isEqualByComparingTo("24");
    assertThat(next.input().datasetVersionId()).isEqualTo(correctedId);

    // Mientras no se publique nada, todas las peticiones comparten la misma instancia en memoria.
    assertThat(datasets.resolve(null)).isSameAs(fresh);
    assertThat(datasets.resolve("2026-27")).isSameAs(fresh);

    publication.publish(originalId);
    assertThat(datasets.resolve(null).info().versionId()).isEqualTo(originalId);
  }

  @Test
  @DisplayName("Importar un borrador sin publicarlo no cambia lo que responde la API")
  void importingADraftDoesNotChangeTheApi() throws Exception {
    String etagBefore = perform(SIMULATION).getResponse().getHeader("ETag");

    Long draftId = importCorrection();

    MockHttpServletResponse after = perform(SIMULATION).getResponse();
    assertThat(after.getHeader("ETag")).isEqualTo(etagBefore);
    assertThat(body(after).get("recognizedEcts").decimalValue()).isEqualByComparingTo("30");
    importer.discardDraft(draftId);
  }

  @Test
  @DisplayName("CORS restringido: solo los orígenes configurados pueden llamar a la API")
  void corsAllowsOnlyConfiguredOrigins() throws Exception {
    MockHttpServletResponse allowed =
        mvc.perform(api("/api/v1/cycles").header("Origin", "https://spa.example")).andReturn().getResponse();
    assertThat(allowed.getStatus()).isEqualTo(200);
    assertThat(allowed.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://spa.example");

    MockHttpServletResponse preflight =
        mvc.perform(
                options("/api/v1/cycles")
                    .header("Origin", "https://otra-spa.example")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn()
            .getResponse();
    assertThat(preflight.getStatus()).isEqualTo(200);
    assertThat(preflight.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://otra-spa.example");

    MockHttpServletResponse denied =
        mvc.perform(api("/api/v1/cycles").header("Origin", "https://intruso.example")).andReturn().getResponse();
    assertThat(denied.getStatus()).isEqualTo(403);
    assertThat(denied.getHeader("Access-Control-Allow-Origin")).isNull();

    // Solo lectura: un origen permitido tampoco puede pedir métodos de escritura.
    MockHttpServletResponse write =
        mvc.perform(
                options("/api/v1/cycles")
                    .header("Origin", "https://spa.example")
                    .header("Access-Control-Request-Method", "POST"))
            .andReturn()
            .getResponse();
    assertThat(write.getStatus()).isEqualTo(403);
  }

  // ---------- utilidades ----------

  private Long currentVersionId() {
    return versions.findByAcademicYearAndStatus("2026-27", Status.PUBLICADO).orElseThrow().getId();
  }

  /** Importa como borrador una corrección de 2026-27: el tope de la tabla T-PR-02 baja de 30 a 24. */
  private Long importCorrection() {
    ImportResult result =
        importer.importDataset(SampleFiles.of("2026-27").replace(TABLES, "S-PR-01,30,", "S-PR-01,24,").build());
    assertThat(result.accepted()).as("errores: " + result.report().errors()).isTrue();
    return result.datasetVersionId();
  }

  private boolean notModified(String etag) throws Exception {
    int status = mvc.perform(api(SIMULATION).header("If-None-Match", etag)).andReturn().getResponse().getStatus();
    return status == 304;
  }

  private JsonNode body(MockHttpServletResponse response) throws Exception {
    return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
  }
}
