package es.convalidafp.admin;

import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import es.convalidafp.testsupport.SampleFiles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Endpoints de administración: subir un dataset, consultar su informe guardado, publicarlo,
 * restaurar una versión y leer la auditoría.
 */
class AdminImportApiTest extends AdminTestSupport {

  private static final String SIMULATION = "/api/v1/simulations?cycle=CF-PR&degree=UPR-GD";

  // ---------- importar ----------

  @Test
  @DisplayName("POST /admin/imports: un dataset válido queda en borrador, con avisos y diferencias")
  void uploadValidDataset() throws Exception {
    MvcResult upload = mvc.perform(adminUpload("correccion.zip", correctionZip())).andReturn();

    assertThat(upload.getResponse().getStatus()).isEqualTo(201);
    JsonNode created = json(upload);
    long importId = created.get("id").asLong();
    assertThat(upload.getResponse().getHeader("Location")).isEqualTo("/api/v1/admin/imports/" + importId);
    assertThat(created.get("outcome").asString()).isEqualTo("ACEPTADA");
    assertThat(created.get("versionStatus").asString()).isEqualTo("BORRADOR");
    assertThat(created.get("createdBy").asString()).isEqualTo(ADMIN);
    assertThat(created.get("fileName").asString()).isEqualTo("correccion.zip");
    assertThat(created.get("academicYear").asString()).isEqualTo("2026-27");
    assertThat(created.get("datasetVersionId").isNumber()).isTrue();
    assertThat(created.get("errorCount").asInt()).isZero();
    assertThat(created.get("warningCount").asInt()).isEqualTo(2);

    // Informe: sin errores, los dos avisos del ejemplo y la diferencia con lo publicado.
    JsonNode report = created.get("report");
    assertThat(report.get("errors")).isEmpty();
    assertThat(values(report.get("warnings"), "code")).containsExactlyInAnyOrder("A1", "A2");
    JsonNode warning = report.get("warnings").get(0);
    assertThat(warning.get("file").asString()).isEqualTo("recognition_rules.csv");
    assertThat(warning.get("row").isNumber()).isTrue();
    assertThat(warning.get("column").asString()).isNotBlank();
    assertThat(warning.get("message").asString()).isNotBlank();
    JsonNode diff = report.get("diff");
    assertThat(diff.get("baselineAcademicYear").asString()).isEqualTo("2026-27");
    JsonNode tables = find(diff.get("files"), "file", TABLES);
    assertThat(tables.get("added")).isEmpty();
    assertThat(tables.get("removed")).isEmpty();
    assertThat(tables.get("changed")).hasSize(1);
    JsonNode change = tables.get("changed").get(0);
    assertThat(change.get("id").asString()).isEqualTo("T-PR-02");
    assertThat(change.get("fields").get(0).get("column").asString()).isEqualTo("table_max_ects");
    assertThat(change.get("fields").get(0).get("before").asString()).isEqualTo("30");
    assertThat(change.get("fields").get(0).get("after").asString()).isEqualTo("24");

    // El informe queda guardado: se recupera idéntico por su identificador.
    assertThat(getJson(adminGet("/api/v1/admin/imports/{id}", importId), 200)).isEqualTo(created);

    // En el listado aparece la primera (la más reciente) y sin el informe.
    JsonNode list = getJson(adminGet("/api/v1/admin/imports"), 200);
    assertThat(list.get(0).get("id").asLong()).isEqualTo(importId);
    assertThat(list.get(0).get("report").isNull()).isTrue();

    // Importar no publica: la API pública sigue dando el resultado anterior.
    assertThat(getJson(get(SIMULATION).with(from(clientIp)), 200).get("recognizedEcts").decimalValue())
        .isEqualByComparingTo("30");

    JsonNode audited = auditOf("IMPORTAR", importId);
    assertThat(audited.get("actor").asString()).isEqualTo(ADMIN);
    assertThat(audited.get("datasetVersionId").asLong()).isEqualTo(created.get("datasetVersionId").asLong());
    assertThat(audited.get("academicYear").asString()).isEqualTo("2026-27");
    assertThat(audited.get("ip").asString()).isEqualTo(clientIp);
    assertThat(audited.get("occurredAt").asString()).startsWith("20");
    assertThat(audited.get("detail").asString()).contains("aceptada");
  }

  @Test
  @DisplayName("POST /admin/imports: un dataset con errores se rechaza y su informe queda guardado")
  void uploadInvalidDataset() throws Exception {
    byte[] zip = SampleFiles.of("2026-27").replace(MODULES, ",14,1", ",0,1").toZip("2026-27");

    JsonNode rejected = getJson(adminUpload("con-errores.zip", zip), 201);

    long importId = rejected.get("id").asLong();
    assertThat(rejected.get("outcome").asString()).isEqualTo("RECHAZADA");
    assertThat(rejected.get("datasetVersionId").isNull()).isTrue();
    assertThat(rejected.get("versionStatus").isNull()).isTrue();
    assertThat(rejected.get("errorCount").asInt()).isEqualTo(1);
    JsonNode error = rejected.get("report").get("errors").get(0);
    assertThat(error.get("code").asString()).isEqualTo("V3");
    assertThat(error.get("file").asString()).isEqualTo("fp_modules.csv");
    assertThat(error.get("row").asInt()).isEqualTo(2);
    assertThat(error.get("column").asString()).isEqualTo("ects");
    assertThat(error.get("message").asString()).contains("mayores que 0");
    assertThat(rejected.get("report").get("diff").isNull()).isTrue();

    // El informe de errores se puede consultar después.
    assertThat(getJson(adminGet("/api/v1/admin/imports/{id}", importId), 200)).isEqualTo(rejected);
    // Y no hay nada que publicar.
    assertProblem(
        mvc.perform(adminPost("/api/v1/admin/imports/{id}/publish", importId)).andReturn().getResponse(), 409, "fue rechazada");
    assertThat(auditOf("IMPORTAR", importId).get("detail").asString()).contains("rechazada");
    assertThat(auditOf("IMPORTAR", importId).get("datasetVersionId").isNull()).isTrue();
  }

  @Test
  @DisplayName("POST /admin/imports: un fichero que no es un ZIP de dataset se rechaza con su informe")
  void uploadGarbage() throws Exception {
    JsonNode notAZip = getJson(adminUpload("notas.txt", "esto no es un zip".getBytes(StandardCharsets.UTF_8)), 201);
    assertThat(notAZip.get("outcome").asString()).isEqualTo("RECHAZADA");
    assertThat(notAZip.get("academicYear").isNull()).isTrue();
    assertThat(notAZip.get("errorCount").asInt()).isEqualTo(10);
    assertThat(values(notAZip.get("report").get("errors"), "code")).containsOnly("F1");

    assertProblem(mvc.perform(adminUpload("vacio.zip", new byte[0])).andReturn().getResponse(), 400, "está vacío");
    MockHttpServletResponse noFile =
        mvc.perform(multipart("/api/v1/admin/imports").with(user(ADMIN).roles("ADMIN")).with(xsrf()))
            .andReturn()
            .getResponse();
    assertThat(noFile.getStatus()).isEqualTo(400);
  }

  // ---------- publicar y restaurar ----------

  @Test
  @DisplayName("Publicar una importación y restaurar la versión anterior: cambia la API pública y queda auditado")
  void publishAndRestore() throws Exception {
    long originalVersionId = getJson(get("/api/v1/dataset").with(from(clientIp)), 200).get("versionId").asLong();
    JsonNode imported = getJson(adminUpload("correccion.zip", correctionZip()), 201);
    long importId = imported.get("id").asLong();
    long newVersionId = imported.get("datasetVersionId").asLong();

    // Publicar.
    JsonNode published = getJson(adminPost("/api/v1/admin/imports/{id}/publish", importId), 200);
    assertThat(published.get("versionStatus").asString()).isEqualTo("PUBLICADO");
    JsonNode simulation = getJson(get(SIMULATION).with(from(clientIp)), 200);
    assertThat(simulation.get("recognizedEcts").decimalValue()).isEqualByComparingTo("24");
    assertThat(simulation.get("dataset").get("versionId").asLong()).isEqualTo(newVersionId);

    JsonNode publishAudit = auditOf("PUBLICAR", importId);
    assertThat(publishAudit.get("actor").asString()).isEqualTo(ADMIN);
    assertThat(publishAudit.get("datasetVersionId").asLong()).isEqualTo(newVersionId);
    assertThat(publishAudit.get("academicYear").asString()).isEqualTo("2026-27");
    assertThat(publishAudit.get("occurredAt").asString()).startsWith("20");

    // No se publica dos veces, ni se restaura algo que no está archivado.
    assertProblem(
        mvc.perform(adminPost("/api/v1/admin/imports/{id}/publish", importId)).andReturn().getResponse(), 409, "ya está publicada");
    assertProblem(
        mvc.perform(adminPost("/api/v1/admin/versions/{id}/restore", newVersionId)).andReturn().getResponse(),
        409,
        "Solo se puede restaurar una versión archivada");

    // La versión anterior ha quedado archivada: se restaura.
    assertThat(statusOf(originalVersionId)).isEqualTo("ARCHIVADO");
    JsonNode restored = getJson(adminPost("/api/v1/admin/versions/{id}/restore", originalVersionId), 200);
    assertThat(restored.get("id").asLong()).isEqualTo(originalVersionId);
    assertThat(restored.get("status").asString()).isEqualTo("PUBLICADO");
    assertThat(restored.get("archivedAt").isNull()).isTrue();
    assertThat(statusOf(newVersionId)).isEqualTo("ARCHIVADO");
    JsonNode after = getJson(get(SIMULATION).with(from(clientIp)), 200);
    assertThat(after.get("recognizedEcts").decimalValue()).isEqualByComparingTo("30");
    assertThat(after.get("dataset").get("versionId").asLong()).isEqualTo(originalVersionId);

    JsonNode restoreAudit = latestAudit("RESTAURAR");
    assertThat(restoreAudit.get("actor").asString()).isEqualTo(ADMIN);
    assertThat(restoreAudit.get("datasetVersionId").asLong()).isEqualTo(originalVersionId);
    assertThat(restoreAudit.get("academicYear").asString()).isEqualTo("2026-27");

    // La importación ahora apunta a una versión archivada: para volver a publicarla hay que restaurarla.
    assertThat(getJson(adminGet("/api/v1/admin/imports/{id}", importId), 200).get("versionStatus").asString())
        .isEqualTo("ARCHIVADO");
    assertProblem(
        mvc.perform(adminPost("/api/v1/admin/imports/{id}/publish", importId)).andReturn().getResponse(), 409, "restáurala");
  }

  @Test
  @DisplayName("Lo que no existe da 404 en Problem Details")
  void notFound() throws Exception {
    assertProblem(mvc.perform(adminGet("/api/v1/admin/imports/999999")).andReturn().getResponse(), 404, "No existe la importación 999999");
    assertProblem(
        mvc.perform(adminPost("/api/v1/admin/imports/999999/publish")).andReturn().getResponse(), 404, "No existe la importación");
    assertProblem(
        mvc.perform(adminPost("/api/v1/admin/versions/999999/restore")).andReturn().getResponse(), 404, "No existe la versión 999999");
  }

  // ---------- auditoría y versiones ----------

  @Test
  @DisplayName("GET /admin/audit: las entradas más recientes primero, con límite")
  void auditListing() throws Exception {
    long importId = getJson(adminUpload("uno.zip", correctionZip()), 201).get("id").asLong();
    long nextImportId = getJson(adminUpload("dos.zip", correctionZip()), 201).get("id").asLong();

    JsonNode latest = getJson(adminGet("/api/v1/admin/audit?limit=2"), 200);
    assertThat(latest).hasSize(2);
    assertThat(latest.get(0).get("importId").asLong()).isEqualTo(nextImportId);
    assertThat(latest.get(1).get("importId").asLong()).isEqualTo(importId);
    assertThat(latest.get(0).get("id").asLong()).isGreaterThan(latest.get(1).get("id").asLong());
    assertThat(values(latest, "action")).containsOnly("IMPORTAR");

    assertThat(getJson(adminGet("/api/v1/admin/audit"), 200).size()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("GET /admin/versions: todas las versiones con su estado")
  void versionListing() throws Exception {
    JsonNode versions = getJson(adminGet("/api/v1/admin/versions"), 200);
    assertThat(versions.size()).isGreaterThanOrEqualTo(2);
    assertThat(values(versions, "academicYear")).contains("2025-26", "2026-27");
    assertThat(values(versions, "status")).contains("PUBLICADO");
    JsonNode first = versions.get(0);
    assertThat(first.get("reviewer").asString()).isEqualTo("Revisor Ficticio");
    assertThat(first.get("createdAt").asString()).startsWith("20");
    assertThat(first.get("id").asLong()).isGreaterThan(versions.get(versions.size() - 1).get("id").asLong());
  }

  // ---------- utilidades ----------

  /** El dataset 2026-27 con una corrección: el tope de la tabla T-PR-02 baja de 30 a 24. */
  private static byte[] correctionZip() {
    return SampleFiles.of("2026-27").replace(TABLES, "S-PR-01,30,", "S-PR-01,24,").toZip(null);
  }

  private JsonNode auditOf(String action, long importId) throws Exception {
    for (JsonNode event : getJson(adminGet("/api/v1/admin/audit?limit=500"), 200)) {
      if (event.get("action").asString().equals(action) && event.get("importId").asLong(-1) == importId) {
        return event;
      }
    }
    throw new AssertionError("No hay auditoría " + action + " de la importación " + importId);
  }

  private JsonNode latestAudit(String action) throws Exception {
    for (JsonNode event : getJson(adminGet("/api/v1/admin/audit?limit=500"), 200)) {
      if (event.get("action").asString().equals(action)) {
        return event;
      }
    }
    throw new AssertionError("No hay auditoría " + action);
  }

  private String statusOf(long versionId) throws Exception {
    return find(getJson(adminGet("/api/v1/admin/versions"), 200), "id", Long.toString(versionId)).get("status").asString();
  }

  private static List<String> values(JsonNode array, String field) {
    List<String> values = new ArrayList<>();
    array.forEach(node -> values.add(node.get(field).asString()));
    return values;
  }

  private static JsonNode find(JsonNode array, String field, String value) {
    for (JsonNode node : array) {
      if (node.get(field).asString().equals(value)) {
        return node;
      }
    }
    throw new AssertionError("No está " + value);
  }
}
