package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Listado de documentos oficiales, para la pantalla «Fuentes y metodología». */
class SourcesApiTest extends ApiTestSupport {

  @Test
  @DisplayName("GET /sources: todos los documentos del curso, ordenados por identificador")
  void sources() throws Exception {
    JsonNode sources = getJson("/api/v1/sources");

    assertThat(ids(sources)).containsExactly("S-EJ-01", "S-EJ-02", "S-EJ-03", "S-EJ-04", "S-FI-01", "S-PR-01");
    JsonNode first = sources.get(0);
    assertThat(first.get("documentType").asString()).isEqualTo("CONVENIO");
    assertThat(first.get("title").asString()).startsWith("Convenio Ficticio");
    assertThat(first.get("issuer").asString()).isEqualTo("Comunidad de Ejemplo");
    assertThat(first.get("publicationDate").asString()).isEqualTo("2025-03-14");
    assertThat(first.get("url").asString()).startsWith("https://uej.example/");
    // Cada elemento es el mismo que devuelve la consulta por identificador.
    assertThat(sources.get(3)).isEqualTo(getJson("/api/v1/sources/S-EJ-04"));
  }

  @Test
  @DisplayName("GET /sources?year=: los documentos de otro curso, con la caché HTTP de siempre")
  void sourcesOfAnotherYear() throws Exception {
    assertThat(ids(getJson("/api/v1/sources?year=2025-26"))).containsExactly("S-EJ-01");
    assertThat(perform("/api/v1/sources").getResponse().getHeader("ETag")).startsWith("\"dataset-");
    assertThat(perform("/api/v1/sources?year=2030-31").getResponse().getStatus()).isEqualTo(404);
  }

  private static List<String> ids(JsonNode array) {
    List<String> ids = new ArrayList<>();
    array.forEach(node -> ids.add(node.get("id").asString()));
    return ids;
  }
}
