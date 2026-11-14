package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/** OpenAPI, límite de peticiones y CORS por defecto de la API pública. */
class ApiInfrastructureTest extends ApiTestSupport {

  @Test
  @DisplayName("La especificación OpenAPI se genera y describe todos los endpoints públicos")
  void openApiSpecificationIsGenerated() throws Exception {
    JsonNode spec = getJson("/v3/api-docs");

    assertThat(spec.get("openapi").asString()).startsWith("3.");
    assertThat(spec.get("info").get("title").asString()).isEqualTo("ConvalidaFP API");
    assertThat(spec.get("info").get("description").asString()).contains("orientativos", "Problem Details");
    JsonNode paths = spec.get("paths");
    for (String path :
        new String[] {
          "/api/v1/cycles",
          "/api/v1/universities",
          "/api/v1/degrees",
          "/api/v1/degrees/{id}/subjects",
          "/api/v1/simulations",
          "/api/v1/comparisons",
          "/api/v1/explore",
          "/api/v1/sources/{id}",
          "/api/v1/dataset"
        }) {
      assertThat(paths.has(path)).as(path).isTrue();
      assertThat(paths.get(path).has("get")).as(path + " es GET").isTrue();
    }
    JsonNode simulation = paths.get("/api/v1/simulations").get("get");
    assertThat(simulation.get("summary").asString()).isEqualTo("Simulación de un ciclo frente a un grado");
    assertThat(simulation.get("parameters")).hasSize(3);
    assertThat(spec.get("components").get("schemas").has("SimulationResponse")).isTrue();
  }

  @Test
  @DisplayName("Límite de 60 peticiones por minuto por IP: la 61 recibe un 429 en Problem Details")
  void rateLimitPerIp() throws Exception {
    for (int i = 1; i <= 60; i++) {
      MockHttpServletResponse ok = perform("/api/v1/cycles").getResponse();
      assertThat(ok.getStatus()).as("petición " + i).isEqualTo(200);
      assertThat(ok.getHeader("X-RateLimit-Limit")).isEqualTo("60");
      assertThat(Long.parseLong(ok.getHeader("X-RateLimit-Remaining"))).isLessThanOrEqualTo(60 - i + 1);
    }

    MockHttpServletResponse limited = perform("/api/v1/cycles").getResponse();
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getContentType()).startsWith("application/problem+json");
    assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, 60L);
    JsonNode problem = json.readTree(limited.getContentAsString(StandardCharsets.UTF_8));
    assertThat(problem.get("status").asInt()).isEqualTo(429);
    assertThat(problem.get("title").asString()).isEqualTo("Demasiadas peticiones");
    assertThat(problem.get("detail").asString()).contains("60 peticiones por minuto");
    assertThat(problem.get("instance").asString()).isEqualTo("/api/v1/cycles");

    // El límite es por IP: otra IP sigue pudiendo llamar.
    MockHttpServletResponse other =
        mvc.perform(
                api("/api/v1/cycles")
                    .with(
                        request -> {
                          request.setRemoteAddr("203.0.113.77");
                          return request;
                        }))
            .andReturn()
            .getResponse();
    assertThat(other.getStatus()).isEqualTo(200);

    // Y solo afecta a la API pública: la documentación sigue respondiendo a la IP limitada.
    assertThat(perform("/v3/api-docs").getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("CORS: sin orígenes configurados, ningún otro dominio puede llamar a la API")
  void corsIsClosedByDefault() throws Exception {
    MockHttpServletResponse simple =
        mvc.perform(api("/api/v1/cycles").header("Origin", "https://otro-sitio.example")).andReturn().getResponse();
    assertThat(simple.getHeader("Access-Control-Allow-Origin")).isNull();

    MockHttpServletResponse preflight =
        mvc.perform(
                options("/api/v1/cycles")
                    .header("Origin", "https://otro-sitio.example")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn()
            .getResponse();
    assertThat(preflight.getHeader("Access-Control-Allow-Origin")).isNull();
  }

  @Test
  @DisplayName("Todos los tests de integración comparten un único contenedor MariaDB")
  void singleDatabaseContainer() {
    assertThat(TestcontainersConfiguration.containerId()).isNotBlank();
    // Hay varias bases de datos (una por contexto de Spring), todas dentro de ese contenedor.
    assertThat(TestcontainersConfiguration.databaseCount()).isGreaterThanOrEqualTo(1);
  }
}
