package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.contract.Dataset;
import es.convalidafp.simulation.SimulationEngine;
import es.convalidafp.simulation.SimulationResult;
import es.convalidafp.testsupport.CsvSimulation;
import es.convalidafp.testsupport.SampleFiles;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/** Simulación, comparación y exploración por la API pública. */
class SimulationApiTest extends ApiTestSupport {

  @Autowired private SimulationEngine engine;
  @Autowired private SimulationService simulations;
  @Autowired private PublishedDatasets datasets;
  @Autowired private MeterRegistry meters;

  private final Dataset csv = Dataset.load(SampleFiles.SAMPLE.resolve("2026-27"));

  // ---------- la conversión de las entidades al motor no pierde nada ----------

  @ParameterizedTest(name = "{0} × {1} → {2}")
  @CsvSource({
    "CF-EJ,      UEJ-GP,     RECOGNIZED",
    "CF-EJ,      UEJ-DG,     RECOGNIZED",
    "CF-EJ,      UEJ-GP-SUR, RECOGNIZED",
    "CF-EJ-2024, UEJ-GP,     RECOGNIZED",
    "CF-EJ,      UPR-GD,     RECOGNIZED",
    "CF-PR,      UPR-GD,     RECOGNIZED",
    "CF-EJ,      UFI-GS,     NO_AGREEMENT",
    "CF-PR,      UEJ-DG,     NO_AGREEMENT",
    "CF-PR,      UEJ-GP,     NO_DATA",
    "CF-EJ-2024, UPR-GD,     NO_DATA",
  })
  @DisplayName("Para cada tabla, la API da el mismo resultado que el motor con los datos de los CSV")
  void apiGivesTheSameResultAsTheEngineOnCsvData(String cycleId, String degreeId, String outcome) throws Exception {
    SimulationResult fromCsv = CsvSimulation.simulate(engine, csv, "2026-27", cycleId, degreeId, null);
    PublishedDataset dataset = datasets.resolve(null);

    // 1. Resultado del motor: las entidades convertidas dan lo mismo que los CSV, campo a campo.
    SimulationResult fromDatabase = simulations.simulate(dataset, cycleId, degreeId);
    assertThat(fromDatabase)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .ignoringFields("input.datasetVersionId")
        .isEqualTo(fromCsv);
    assertThat(fromDatabase.input().datasetVersionId()).isEqualTo(dataset.info().versionId());

    // 2. Respuesta HTTP completa: idéntica a la que se obtiene del resultado calculado con los CSV.
    JsonNode expected = json.readTree(json.writeValueAsString(SimulationDtos.toResponse(dataset, fromCsv)));
    JsonNode actual = getJson("/api/v1/simulations?cycle={cycle}&degree={degree}", cycleId, degreeId);
    assertThat(actual).isEqualTo(expected);
    assertThat(actual.get("outcome").asString()).isEqualTo(outcome);
  }

  // ---------- simulación ----------

  @Test
  @DisplayName("GET /simulations: reconocimiento con asignaturas, fuentes, tope y asignaturas restantes")
  void simulation() throws Exception {
    JsonNode result = getJson("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP");

    assertThat(result.get("outcome").asString()).isEqualTo("RECOGNIZED");
    assertThat(result.get("dataset").get("academicYear").asString()).isEqualTo("2026-27");
    assertThat(result.get("cycle").get("id").asString()).isEqualTo("CF-EJ");
    assertThat(result.get("degree").get("universityName").asString()).isEqualTo("Universidad de Ejemplo");
    assertThat(result.get("tableId").asString()).isEqualTo("T-EJ-01");
    // Regla 5: el resultado dice que es orientativo y que conlleva tasas.
    assertThat(result.get("notice").asString()).contains("orientativo", "no vinculante", "universidad", "tasas");

    assertThat(result.get("recognizedEcts").decimalValue()).isEqualByComparingTo("51");
    assertThat(result.get("rulesTotalEcts").decimalValue()).isEqualByComparingTo("51");
    assertThat(result.get("legalCapEcts").decimalValue()).isEqualByComparingTo("60");
    assertThat(result.get("tableCapEcts").isNull()).isTrue();
    assertThat(result.get("capped").asBoolean()).isFalse();
    assertThat(result.get("degreeTotalEcts").decimalValue()).isEqualByComparingTo("240");
    assertThat(result.get("pendingEcts").decimalValue()).isEqualByComparingTo("189");
    assertThat(result.get("recognizedPercentage").decimalValue()).isEqualByComparingTo("21.25");

    assertThat(result.get("recognizedSubjects")).hasSize(8);
    JsonNode web = find(result.get("recognizedSubjects"), "subjectId", "UEJ-GP-WEB");
    assertThat(web.get("ruleId").asString()).isEqualTo("R-006");
    assertThat(values(web.get("alternativeRuleIds"), null)).containsExactly("R-007");
    assertThat(web.get("ects").decimalValue()).isEqualByComparingTo("6");
    // Regla 3: cada reconocimiento lleva su documento, con tipo, título, fecha, URL y página.
    JsonNode source = web.get("source");
    assertThat(source.get("sourceId").asString()).isEqualTo("S-EJ-01");
    assertThat(source.get("documentType").asString()).isEqualTo("CONVENIO");
    assertThat(source.get("title").asString()).startsWith("Convenio Ficticio");
    assertThat(source.get("publicationDate").asString()).isEqualTo("2025-03-14");
    assertThat(source.get("url").asString()).isEqualTo("https://uej.example/docs/convenio-ficticio.pdf");
    assertThat(source.get("page").asInt()).isEqualTo(3);
    assertThat(source.get("excerpt").asString()).isEqualTo("Módulo Ficticio 07 - Desarrollo Web");
    // La regla con fuente propia cita su adenda, no el convenio de la tabla.
    assertThat(find(result.get("recognizedSubjects"), "subjectId", "UEJ-GP-IS1").get("source").get("sourceId").asString())
        .isEqualTo("S-EJ-04");

    assertThat(values(result.get("optativeCredits"), "ruleId")).containsExactly("R-003");
    assertThat(values(result.get("conditionalRecognitions"), "ruleId")).containsExactly("R-004");
    // subjects_complete = true: el dataset trae todas las asignaturas y se listan las restantes.
    assertThat(result.get("subjectsComplete").asBoolean()).isTrue();
    assertThat(values(result.get("remainingByYear"), "courseYear")).containsExactly("1", "2", "3", "4");
    assertThat(result.get("noAgreement").isNull()).isTrue();
  }

  @Test
  @DisplayName("subjects_complete = false: sin lista de restantes y con los mismos ECTS pendientes")
  void simulationWithIncompleteSubjectList() throws Exception {
    JsonNode result = getJson("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP-SUR");

    assertThat(result.get("outcome").asString()).isEqualTo("RECOGNIZED");
    assertThat(result.get("subjectsComplete").asBoolean()).isFalse();
    assertThat(result.get("remainingByYear")).isEmpty();
    // ECTS pendientes = total_ects del grado - reconocidos, como siempre.
    assertThat(result.get("recognizedEcts").decimalValue()).isEqualByComparingTo("22.5");
    assertThat(result.get("degreeTotalEcts").decimalValue()).isEqualByComparingTo("240");
    assertThat(result.get("pendingEcts").decimalValue()).isEqualByComparingTo("217.5");
    assertThat(result.get("recognizedSubjects")).isNotEmpty();
    // La pantalla enlaza la web del grado para consultar el plan completo.
    assertThat(result.get("degree").get("url").asString()).isEqualTo("https://uej.example/sur/grados/prueba");

    // También sin reconocimiento calculado: UFI-GS tiene subjects_complete vacío (= false).
    assertThat(getJson("/api/v1/simulations?cycle=CF-EJ&degree=UFI-GS").get("subjectsComplete").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Tope: 66 ECTS limitados a 60 sin descartar asignaturas (caso 5) y tope de tabla (caso 6)")
  void cappedSimulations() throws Exception {
    JsonNode legal = getJson("/api/v1/simulations?cycle=CF-EJ&degree=UPR-GD");
    assertThat(legal.get("capped").asBoolean()).isTrue();
    assertThat(legal.get("rulesTotalEcts").decimalValue()).isEqualByComparingTo("66");
    assertThat(legal.get("recognizedEcts").decimalValue()).isEqualByComparingTo("60");
    assertThat(legal.get("recognizedSubjects")).hasSize(9);
    assertThat(legal.get("recognizedPercentage").decimalValue()).isEqualByComparingTo("25");

    JsonNode table = getJson("/api/v1/simulations?cycle=CF-PR&degree=UPR-GD");
    assertThat(table.get("capped").asBoolean()).isTrue();
    assertThat(table.get("tableCapEcts").decimalValue()).isEqualByComparingTo("30");
    assertThat(table.get("appliedCapEcts").decimalValue()).isEqualByComparingTo("30");
    assertThat(table.get("recognizedEcts").decimalValue()).isEqualByComparingTo("30");
  }

  @Test
  @DisplayName("Sin convenio y sin datos son resultados válidos (200) y distintos (caso 1)")
  void noAgreementAndNoData() throws Exception {
    JsonNode noAgreement = getJson("/api/v1/simulations?cycle=CF-EJ&degree=UFI-GS");
    assertThat(noAgreement.get("outcome").asString()).isEqualTo("NO_AGREEMENT");
    assertThat(noAgreement.get("tableId").asString()).isEqualTo("T-FI-01");
    assertThat(noAgreement.get("noAgreement").get("source").get("sourceId").asString()).isEqualTo("S-FI-01");
    assertThat(noAgreement.get("noAgreement").get("notes").asString()).contains("no existe tabla firmada");
    assertThat(noAgreement.get("recognizedEcts").isNull()).isTrue();
    assertThat(noAgreement.get("capped").isNull()).isTrue();
    assertThat(noAgreement.get("recognizedSubjects")).isEmpty();
    assertThat(noAgreement.get("notice").asString()).contains("orientativo");

    JsonNode withoutSource = getJson("/api/v1/simulations?cycle=CF-PR&degree=UEJ-DG");
    assertThat(withoutSource.get("outcome").asString()).isEqualTo("NO_AGREEMENT");
    assertThat(withoutSource.get("noAgreement").get("source").isNull()).isTrue();

    JsonNode noData = getJson("/api/v1/simulations?cycle=CF-PR&degree=UEJ-GP");
    assertThat(noData.get("outcome").asString()).isEqualTo("NO_DATA");
    assertThat(noData.get("tableId").isNull()).isTrue();
    assertThat(noData.get("noAgreement").isNull()).isTrue();
    assertThat(noData.get("degreeTotalEcts").decimalValue()).isEqualByComparingTo("240");
  }

  @Test
  @DisplayName("El parámetro year simula con la versión publicada de ese curso")
  void simulationOfAnotherYear() throws Exception {
    JsonNode previous = getJson("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP&year=2025-26");
    assertThat(previous.get("dataset").get("academicYear").asString()).isEqualTo("2025-26");
    assertThat(previous.get("recognizedEcts").decimalValue()).isEqualByComparingTo("30");
    assertThat(find(previous.get("recognizedSubjects"), "subjectId", "UEJ-GP-WEB").get("name").asString())
        .isEqualTo("Programación Web de Prueba");
  }

  @Test
  @DisplayName("Errores de simulación: parámetro que falta (400) y ciclo o grado inexistentes (404)")
  void simulationErrors() throws Exception {
    assertProblem(perform("/api/v1/simulations?degree=UEJ-GP").getResponse(), 400, "Falta el parámetro obligatorio «cycle»");
    assertProblem(perform("/api/v1/simulations?cycle=CF-EJ").getResponse(), 400, "Falta el parámetro obligatorio «degree»");
    assertProblem(perform("/api/v1/simulations?cycle=CF-NO&degree=UEJ-GP").getResponse(), 404, "el ciclo «CF-NO»");
    assertProblem(perform("/api/v1/simulations?cycle=CF-EJ&degree=NADA").getResponse(), 404, "el grado «NADA»");
    // En 2025-26 ese grado todavía no existe.
    assertProblem(
        perform("/api/v1/simulations?cycle=CF-EJ&degree=UPR-GD&year=2025-26").getResponse(), 404, "curso 2025-26");
  }

  @Test
  @DisplayName("La métrica del tiempo de simulación cuenta cada simulación")
  void simulationTimeIsMeasured() throws Exception {
    long before = meters.get(SimulationService.TIMER_NAME).timer().count();
    getJson("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP");
    assertThat(meters.get(SimulationService.TIMER_NAME).timer().count()).isEqualTo(before + 1);
  }

  // ---------- comparador ----------

  @Test
  @DisplayName("GET /comparisons: un ciclo frente a 4 grados, en el orden pedido")
  void comparison() throws Exception {
    JsonNode comparison = getJson("/api/v1/comparisons?cycle=CF-EJ&degrees=UPR-GD,UEJ-GP,UFI-GS,UEJ-DG");

    assertThat(comparison.get("cycle").get("id").asString()).isEqualTo("CF-EJ");
    assertThat(comparison.get("dataset").get("academicYear").asString()).isEqualTo("2026-27");
    JsonNode results = comparison.get("results");
    assertThat(results).hasSize(4);
    List<String> degreeIds = new ArrayList<>();
    results.forEach(r -> degreeIds.add(r.get("degree").get("id").asString()));
    assertThat(degreeIds).containsExactly("UPR-GD", "UEJ-GP", "UFI-GS", "UEJ-DG");
    assertThat(values(results, "outcome")).containsExactly("RECOGNIZED", "RECOGNIZED", "NO_AGREEMENT", "RECOGNIZED");
    assertThat(results.get(3).get("recognizedEcts").decimalValue()).isEqualByComparingTo("75");
    // Cada resultado es el mismo que da la simulación individual.
    assertThat(results.get(1)).isEqualTo(getJson("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP"));

    // Los repetidos y los espacios se ignoran.
    assertThat(getJson("/api/v1/comparisons?cycle=CF-EJ&degrees=UEJ-GP, UEJ-GP ,UEJ-DG").get("results")).hasSize(2);
  }

  @Test
  @DisplayName("GET /comparisons: como máximo 4 grados y todos deben existir")
  void comparisonErrors() throws Exception {
    assertProblem(
        perform("/api/v1/comparisons?cycle=CF-EJ&degrees=UPR-GD,UEJ-GP,UFI-GS,UEJ-DG,UEJ-GP-SUR").getResponse(),
        400,
        "entre 1 y 4 grados");
    assertProblem(perform("/api/v1/comparisons?cycle=CF-EJ&degrees=,").getResponse(), 400, "entre 1 y 4 grados");
    assertProblem(perform("/api/v1/comparisons?cycle=CF-EJ").getResponse(), 400, "«degrees»");
    assertProblem(perform("/api/v1/comparisons?cycle=CF-EJ&degrees=UEJ-GP,NADA").getResponse(), 404, "el grado «NADA»");
  }

  // ---------- explorador ----------

  @Test
  @DisplayName("GET /explore: grados que reconocen algo desde un ciclo, de más a menos ECTS")
  void explore() throws Exception {
    JsonNode explore = getJson("/api/v1/explore?cycle=CF-EJ");

    JsonNode results = explore.get("results");
    List<String> degreeIds = new ArrayList<>();
    results.forEach(r -> degreeIds.add(r.get("degree").get("id").asString()));
    // 75, 60, 51 y 22.5 ECTS. UFI-GS (sin convenio) no reconoce nada y no aparece.
    assertThat(degreeIds).containsExactly("UEJ-DG", "UPR-GD", "UEJ-GP", "UEJ-GP-SUR");
    assertThat(results.get(0).get("recognizedEcts").decimalValue()).isEqualByComparingTo("75");
    assertThat(results.get(1).get("capped").asBoolean()).isTrue();
    assertThat(results.get(1).get("rulesTotalEcts").decimalValue()).isEqualByComparingTo("66");
    assertThat(results.get(3).get("recognizedEcts").decimalValue()).isEqualByComparingTo("22.5");
    assertThat(results.get(2).get("recognizedSubjectCount").asInt()).isEqualTo(8);
    assertThat(explore.get("notice").asString()).contains("orientativo");

    assertThat(values(getJson("/api/v1/explore?cycle=CF-PR").get("results"), "capped")).containsExactly("true");
    assertProblem(perform("/api/v1/explore?cycle=CF-NO").getResponse(), 404, "el ciclo «CF-NO»");
  }

  // ---------- utilidades ----------

  private void assertProblem(MockHttpServletResponse response, int status, String detailFragment) throws Exception {
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(problem.get("status").asInt()).isEqualTo(status);
    assertThat(problem.get("detail").asString()).contains(detailFragment);
  }

  private static List<String> values(JsonNode array, String field) {
    List<String> values = new ArrayList<>();
    array.forEach(node -> values.add(field == null ? node.asString() : node.get(field).asString()));
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
