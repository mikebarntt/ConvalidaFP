package es.convalidafp.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Quién puede entrar a cada ruta, con la cadena de filtros de Spring Security activa: la API
 * pública sigue abierta y sin sesión, y la de administración exige iniciar sesión con rol ADMIN.
 */
class SecurityAccessTest extends AdminTestSupport {

  // ---------- rutas públicas ----------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/dataset",
        "/api/v1/cycles",
        "/api/v1/universities",
        "/api/v1/degrees",
        "/api/v1/degrees/UEJ-GP/subjects",
        "/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP",
        "/api/v1/comparisons?cycle=CF-EJ&degrees=UEJ-GP,UEJ-DG",
        "/api/v1/explore?cycle=CF-EJ",
        "/api/v1/sources/S-EJ-01",
        "/v3/api-docs",
        "/actuator/health"
      })
  @DisplayName("Cada ruta pública responde sin login, sin crear sesión y sin cookies")
  void publicRoutesAreOpenAndStateless(String url) throws Exception {
    MvcResult result = mvc.perform(get(url).with(from(clientIp))).andReturn();

    assertThat(result.getResponse().getStatus()).as(url).isEqualTo(200);
    assertThat(result.getRequest().getSession(false)).as("no se crea sesión").isNull();
    assertThat(result.getResponse().getCookies()).as("no se envía ninguna cookie").isEmpty();
  }

  @Test
  @DisplayName("La documentación Swagger UI es pública")
  void swaggerUiIsPublic() throws Exception {
    int status = mvc.perform(get("/swagger-ui.html").with(from(clientIp))).andReturn().getResponse().getStatus();
    assertThat(status).isIn(200, 302);
  }

  @Test
  @DisplayName("La API pública conserva sus cabeceras de caché con la seguridad activa")
  void publicApiKeepsItsCacheHeaders() throws Exception {
    MockHttpServletResponse response = mvc.perform(get("/api/v1/cycles").with(from(clientIp))).andReturn().getResponse();
    assertThat(response.getHeader("ETag")).startsWith("\"dataset-");
    assertThat(response.getHeader("Cache-Control")).contains("max-age=300").contains("public");
  }

  // ---------- rutas de administración ----------

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource({
    "GET,  /api/v1/admin/session",
    "GET,  /api/v1/admin/imports",
    "GET,  /api/v1/admin/imports/1",
    "POST, /api/v1/admin/imports",
    "POST, /api/v1/admin/imports/1/publish",
    "GET,  /api/v1/admin/versions",
    "POST, /api/v1/admin/versions/1/restore",
    "GET,  /api/v1/admin/audit",
  })
  @DisplayName("Cada ruta de admin da 401 sin login y 403 con un usuario sin rol ADMIN")
  void adminRoutesRequireAnAdmin(String method, String url) throws Exception {
    // Sin login: 401. En los POST se envía un token CSRF válido para comprobar la autenticación.
    MockHttpServletResponse anonymous = mvc.perform(adminRequest(method, url).with(xsrf())).andReturn().getResponse();
    assertProblem(anonymous, 401, "Inicia sesión como administrador");

    // Con un usuario autenticado que no es administrador: 403.
    MockHttpServletResponse notAdmin =
        mvc.perform(adminRequest(method, url).with(xsrf()).with(user("alguien").roles("USER"))).andReturn().getResponse();
    assertProblem(notAdmin, 403, "No tienes permiso");

    // Con un administrador ya no es ni 401 ni 403: la petición llega al controlador.
    int asAdmin =
        mvc.perform(adminRequest(method, url).with(xsrf()).with(user(ADMIN).roles("ADMIN")))
            .andReturn()
            .getResponse()
            .getStatus();
    assertThat(asAdmin).isNotIn(401, 403);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/v1/admin/imports", "/api/v1/admin/imports/1/publish", "/api/v1/admin/versions/1/restore"})
  @DisplayName("CSRF activo: un POST de admin sin token da 403 aunque sea un administrador")
  void adminPostsRequireCsrfToken(String url) throws Exception {
    MockHttpServletResponse response =
        mvc.perform(adminRequest("POST", url).with(user(ADMIN).roles("ADMIN"))).andReturn().getResponse();
    assertProblem(response, 403, "token CSRF");

    // Tampoco vale un token inventado.
    MockHttpServletResponse invalid =
        mvc.perform(adminRequest("POST", url).with(user(ADMIN).roles("ADMIN")).with(invalidXsrf()))
            .andReturn()
            .getResponse();
    assertThat(invalid.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("Una ruta de admin que no existe también exige login: no se filtra nada sin autenticar")
  void unknownAdminRouteAlsoRequiresLogin() throws Exception {
    assertProblem(mvc.perform(get("/api/v1/admin/no-existe")).andReturn().getResponse(), 401, "Inicia sesión");
  }

  @Test
  @DisplayName("El token CSRF se puede pedir sin sesión: hace falta para poder hacer login")
  void csrfTokenIsAvailableWithoutLogin() throws Exception {
    MvcResult result = mvc.perform(get("/api/v1/admin/csrf")).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(result).get("headerName").asString()).isEqualTo("X-XSRF-TOKEN");
    assertThat(json(result).get("token").asString()).isNotBlank();
  }

  private MockHttpServletRequestBuilder adminRequest(String method, String url) {
    return request(HttpMethod.valueOf(method), url).with(from(clientIp));
  }
}
