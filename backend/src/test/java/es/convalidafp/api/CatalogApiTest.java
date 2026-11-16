package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** Endpoints de catálogo de la API pública, cabeceras de caché y errores en Problem Details. */
class CatalogApiTest extends ApiTestSupport {

  // ---------- catálogo ----------

  @Test
  @DisplayName("GET /dataset: versión publicada, curso vigente y cursos disponibles")
  void dataset() throws Exception {
    JsonNode dataset = getJson("/api/v1/dataset");
    assertThat(dataset.get("versionId").isNumber()).isTrue();
    assertThat(dataset.get("academicYear").asString()).isEqualTo("2026-27");
    assertThat(dataset.get("publishedAt").asString()).startsWith("20");
    assertThat(ids(dataset.get("availableYears"), null)).containsExactly("2026-27", "2025-26");

    JsonNode previous = getJson("/api/v1/dataset?year=2025-26");
    assertThat(previous.get("academicYear").asString()).isEqualTo("2025-26");
    assertThat(previous.get("versionId").asLong()).isNotEqualTo(dataset.get("versionId").asLong());
  }

  @Test
  @DisplayName("GET /cycles: ciclos disponibles")
  void cycles() throws Exception {
    JsonNode cycles = getJson("/api/v1/cycles");
    assertThat(ids(cycles, "id")).containsExactly("CF-EJ", "CF-EJ-2024", "CF-PR");
    JsonNode first = cycles.get(0);
    assertThat(first.get("name").asString()).isEqualTo("Ciclo Ficticio de Desarrollo de Aplicaciones de Ejemplo");
    assertThat(first.get("regulationRef").asString()).isEqualTo("Norma Ficticia 01/2010");
    assertThat(first.get("totalEcts").decimalValue()).isEqualByComparingTo("120");
    // help_text: ayuda para distinguir dos ciclos con el mismo nombre y distinta normativa (opcional).
    assertThat(first.get("helpText").asString()).contains("Norma Ficticia 01/2010");
    assertThat(cycles.get(1).get("helpText").asString()).contains("Norma Ficticia 02/2024");
    assertThat(cycles.get(2).get("helpText").isNull()).isTrue();
  }

  @Test
  @DisplayName("GET /universities: universidades, con tildes, comillas y comas intactas (caso 15)")
  void universities() throws Exception {
    JsonNode universities = getJson("/api/v1/universities");
    assertThat(ids(universities, "id")).containsExactly("UEJ", "UFI", "UPR");
    assertThat(universities.get(1).get("name").asString())
        .isEqualTo("Universidad Ficticia de Tecnología \"San Ejemplo\", Campus Norte");
    assertThat(universities.get(1).get("ownership").asString()).isEqualTo("PRIVADA");
    // Celda vacía = desconocido = null, nunca una cadena vacía ni un cero.
    assertThat(universities.get(1).get("websiteUrl").isNull()).isTrue();
  }

  @Test
  @DisplayName("GET /degrees: todos, por universidad y por ciclo (solo los que tienen tabla)")
  void degrees() throws Exception {
    assertThat(ids(getJson("/api/v1/degrees"), "id"))
        .containsExactly("UEJ-DG", "UEJ-GP", "UEJ-GP-SUR", "UFI-GS", "UPR-GD");

    JsonNode ofUniversity = getJson("/api/v1/degrees?university=UEJ");
    assertThat(ids(ofUniversity, "id")).containsExactly("UEJ-DG", "UEJ-GP", "UEJ-GP-SUR");
    assertThat(ofUniversity.get(0).get("universityName").asString()).isEqualTo("Universidad de Ejemplo");
    assertThat(ofUniversity.get(0).get("degreeType").asString()).isEqualTo("DOBLE");
    assertThat(ofUniversity.get(0).get("totalEcts").decimalValue()).isEqualByComparingTo("372");
    assertThat(ofUniversity.get(0).get("tableStatus").isNull()).isTrue();

    // Con ciclo: solo los grados con fila para ese ciclo, indicando si hay tabla o no hay convenio.
    JsonNode forCycle = getJson("/api/v1/degrees?cycle=CF-PR");
    assertThat(ids(forCycle, "id")).containsExactly("UEJ-DG", "UPR-GD");
    assertThat(ids(forCycle, "tableStatus")).containsExactly("SIN_CONVENIO", "VIGENTE");

    assertThat(ids(getJson("/api/v1/degrees?cycle=CF-EJ&university=UEJ"), "id"))
        .containsExactly("UEJ-DG", "UEJ-GP", "UEJ-GP-SUR");
    assertThat(getJson("/api/v1/degrees?cycle=CF-EJ-2024&university=UPR")).isEmpty();
  }

  @Test
  @DisplayName("GET /degrees/{id}/subjects: asignaturas, con decimales y valores desconocidos")
  void subjects() throws Exception {
    JsonNode subjects = getJson("/api/v1/degrees/{id}/subjects", "UEJ-GP");
    assertThat(subjects).hasSize(40);
    JsonNode lm = find(subjects, "UEJ-GP-LM");
    assertThat(lm.get("ects").decimalValue()).isEqualByComparingTo("4.5");
    assertThat(lm.get("subjectType").asString()).isEqualTo("OBLIGATORIA");
    assertThat(lm.get("courseYear").asInt()).isEqualTo(2);

    JsonNode unknown = find(getJson("/api/v1/degrees/{id}/subjects", "UFI-GS"), "UFI-GS-SIS");
    assertThat(unknown.get("ects").isNull()).isTrue();
  }

  @Test
  @DisplayName("GET /sources/{id}: documento oficial de una regla")
  void source() throws Exception {
    JsonNode source = getJson("/api/v1/sources/{id}", "S-EJ-04");
    assertThat(source.get("documentType").asString()).isEqualTo("ADENDA");
    assertThat(source.get("title").asString()).startsWith("Adenda Ficticia n.º 2");
    assertThat(source.get("publicationDate").asString()).isEqualTo("2026-07-15");
    assertThat(source.get("url").asString()).isEqualTo("https://uej.example/docs/adenda-ficticia-2.pdf");
    assertThat(source.get("fileSha256").isNull()).isTrue();
  }

  @Test
  @DisplayName("El parámetro year elige el curso: en 2025-26 la asignatura tiene su nombre antiguo (caso 11)")
  void yearParameterSelectsThePublishedVersionOfThatYear() throws Exception {
    assertThat(ids(getJson("/api/v1/degrees?year=2025-26"), "id")).containsExactly("UEJ-GP");
    assertThat(find(getJson("/api/v1/degrees/UEJ-GP/subjects?year=2025-26"), "UEJ-GP-WEB").get("name").asString())
        .isEqualTo("Programación Web de Prueba");
    assertThat(find(getJson("/api/v1/degrees/UEJ-GP/subjects"), "UEJ-GP-WEB").get("name").asString())
        .isEqualTo("Desarrollo Web de Prueba");
  }

  // ---------- caché HTTP ----------

  @Test
  @DisplayName("Las respuestas llevan ETag de la versión publicada y Cache-Control; If-None-Match da 304")
  void etagAndCacheControl() throws Exception {
    MockHttpServletResponse cycles = perform("/api/v1/cycles").getResponse();
    String etag = cycles.getHeader("ETag");
    assertThat(etag).startsWith("\"dataset-").endsWith("\"");
    assertThat(cycles.getHeader("Cache-Control")).contains("max-age=300").contains("public");

    // Mismo ETag en todos los endpoints de la misma versión; distinto en otro curso.
    assertThat(perform("/api/v1/universities").getResponse().getHeader("ETag")).isEqualTo(etag);
    assertThat(perform("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP").getResponse().getHeader("ETag")).isEqualTo(etag);
    assertThat(perform("/api/v1/cycles?year=2025-26").getResponse().getHeader("ETag")).isNotEqualTo(etag);

    MockHttpServletResponse notModified =
        mvc.perform(api("/api/v1/cycles").header("If-None-Match", etag)).andReturn().getResponse();
    assertThat(notModified.getStatus()).isEqualTo(304);
    assertThat(notModified.getContentAsString()).isEmpty();
    assertThat(notModified.getHeader("ETag")).isEqualTo(etag);

    MockHttpServletResponse modified =
        mvc.perform(api("/api/v1/cycles").header("If-None-Match", "\"dataset-0-0\"")).andReturn().getResponse();
    assertThat(modified.getStatus()).isEqualTo(200);
  }

  // ---------- errores en Problem Details ----------

  @Test
  @DisplayName("404 en Problem Details y en español cuando algo no existe")
  void notFoundProblems() throws Exception {
    assertProblem(perform("/api/v1/degrees?cycle=CF-NO"), 404, "No encontrado", "No existe el ciclo «CF-NO»");
    assertProblem(perform("/api/v1/degrees?university=NADIE"), 404, "No encontrado", "la universidad «NADIE»");
    assertProblem(perform("/api/v1/degrees/NADA/subjects"), 404, "No encontrado", "el grado «NADA»");
    assertProblem(perform("/api/v1/sources/S-NO"), 404, "No encontrado", "el documento «S-NO»");
    assertProblem(perform("/api/v1/cycles?year=2030-31"), 404, "No encontrado", "No hay datos publicados para el curso 2030-31");
    assertProblem(perform("/api/v1/no-existe"), 404, "No encontrado", "La dirección solicitada no existe");
  }

  @Test
  @DisplayName("400 en Problem Details cuando el curso no tiene el formato AAAA-AA")
  void invalidYearProblem() throws Exception {
    MvcResult result = perform("/api/v1/cycles?year=2026");
    assertProblem(result, 400, "Petición no válida", "AAAA-AA");
    assertThat(body(result).get("instance").asString()).isEqualTo("/api/v1/cycles");
  }

  // ---------- utilidades ----------

  private void assertProblem(MvcResult result, int status, String title, String detailFragment) throws Exception {
    MockHttpServletResponse response = result.getResponse();
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode problem = body(result);
    assertThat(problem.get("status").asInt()).isEqualTo(status);
    assertThat(problem.get("title").asString()).isEqualTo(title);
    assertThat(problem.get("detail").asString()).contains(detailFragment);
    // RFC 9457: «type» es opcional y, si falta, vale about:blank.
    assertThat(problem.path("type").asString("about:blank")).isEqualTo("about:blank");
  }

  /** Valores de un campo de cada elemento (o los propios elementos si field es null). */
  private static List<String> ids(JsonNode array, String field) {
    List<String> values = new ArrayList<>();
    array.forEach(node -> values.add(field == null ? node.asString() : node.get(field).asString()));
    return values;
  }

  private static JsonNode find(JsonNode array, String id) {
    for (JsonNode node : array) {
      if (node.get("id").asString().equals(id)) {
        return node;
      }
    }
    throw new AssertionError("No está " + id);
  }
}
