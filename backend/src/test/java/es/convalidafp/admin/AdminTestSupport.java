package es.convalidafp.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import es.convalidafp.api.RateLimitFilter;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base de los tests de administración y seguridad: la aplicación completa contra MariaDB, con la
 * cadena de filtros de Spring Security, el dataset de ejemplo publicado y un primer administrador
 * creado al arrancar a partir de las propiedades de entorno (con valores solo de prueba).
 */
@SpringBootTest(
    properties = {
      "convalidafp.bootstrap.sample-dataset.enabled=true",
      "convalidafp.bootstrap.sample-dataset.dir=${convalidafp.dataDir:../data}/sample",
      "convalidafp.admin.bootstrap.username=" + AdminTestSupport.ADMIN,
      "convalidafp.admin.bootstrap.password=" + AdminTestSupport.PASSWORD,
      "test.context=admin"
    })
@Import(TestcontainersConfiguration.class)
abstract class AdminTestSupport {

  static final String ADMIN = "admin-de-prueba";
  static final String PASSWORD = "clave-de-prueba-0123456789";

  private static final AtomicInteger CLIENTS = new AtomicInteger();

  @Autowired private WebApplicationContext context;
  @Autowired private RateLimitFilter rateLimitFilter;
  @Autowired protected ObjectMapper json;

  protected MockMvc mvc;
  protected String clientIp;

  @BeforeEach
  void setUpHttpClient() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(rateLimitFilter).build();
    clientIp = newIp();
  }

  /** Una IP distinta cada vez, para que los límites por IP de un test no afecten a otro. */
  protected static String newIp() {
    int n = CLIENTS.incrementAndGet();
    return "10.9." + ((n >> 8) & 255) + "." + (n & 255);
  }

  protected static RequestPostProcessor from(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  /**
   * Un token CSRF válido, como lo envía la SPA: el mismo valor en la cookie XSRF-TOKEN y en la
   * cabecera X-XSRF-TOKEN. No se usa la utilidad csrf() de spring-security-test porque sustituye el
   * almacén de tokens del contexto y dejaría de probarse el mecanismo real.
   */
  protected static RequestPostProcessor xsrf() {
    return xsrf("token-de-prueba-0123456789", "token-de-prueba-0123456789");
  }

  /** Un token CSRF que no vale: la cabecera no coincide con la cookie. */
  protected static RequestPostProcessor invalidXsrf() {
    return xsrf("token-de-prueba-0123456789", "otro-token-distinto");
  }

  private static RequestPostProcessor xsrf(String cookieValue, String headerValue) {
    return request -> {
      request.setCookies(new Cookie("XSRF-TOKEN", cookieValue));
      request.addHeader("X-XSRF-TOKEN", headerValue);
      return request;
    };
  }

  // ---------- peticiones con un administrador ya autenticado (sin pasar por el login) ----------

  protected MockHttpServletRequestBuilder adminGet(String url, Object... variables) {
    return get(url, variables).with(from(clientIp)).with(user(ADMIN).roles("ADMIN"));
  }

  protected MockHttpServletRequestBuilder adminPost(String url, Object... variables) {
    return post(url, variables).with(from(clientIp)).with(user(ADMIN).roles("ADMIN")).with(xsrf());
  }

  protected RequestBuilder adminUpload(String fileName, byte[] content) {
    return multipart("/api/v1/admin/imports")
        .file(new MockMultipartFile("file", fileName, "application/zip", content))
        .with(from(clientIp))
        .with(user(ADMIN).roles("ADMIN"))
        .with(xsrf());
  }

  protected JsonNode json(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  protected JsonNode getJson(RequestBuilder request, int expectedStatus) throws Exception {
    MvcResult result = mvc.perform(request).andReturn();
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(expectedStatus);
    return json(result);
  }

  protected void assertProblem(MockHttpServletResponse response, int status, String detailFragment) throws Exception {
    assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(status);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    assertThat(problem.get("status").asInt()).isEqualTo(status);
    assertThat(problem.get("title").asString()).isNotBlank();
    assertThat(problem.get("detail").asString()).contains(detailFragment);
  }

  // ---------- login de verdad: token CSRF, credenciales y sesión ----------

  /** Una sesión iniciada: la sesión HTTP y el token CSRF que hay que enviar en cada POST. */
  protected record AdminSession(MockHttpSession session, String csrfToken) {

    MockHttpServletRequestBuilder apply(MockHttpServletRequestBuilder request) {
      return request.session(session).cookie(new Cookie("XSRF-TOKEN", csrfToken)).header("X-XSRF-TOKEN", csrfToken);
    }

    MockHttpServletRequestBuilder withoutCsrf(MockHttpServletRequestBuilder request) {
      return request.session(session);
    }
  }

  /** Pide el token CSRF y envía las credenciales. Devuelve la respuesta del login tal cual. */
  protected MvcResult attemptLogin(String username, String password, String ip) throws Exception {
    MvcResult csrf = mvc.perform(get("/api/v1/admin/csrf").with(from(ip))).andReturn();
    assertThat(csrf.getResponse().getStatus()).isEqualTo(200);
    String token = json(csrf).get("token").asString();
    assertThat(csrf.getResponse().getCookie("XSRF-TOKEN").getValue()).isEqualTo(token);
    return mvc.perform(
            post("/api/v1/admin/login")
                .with(from(ip))
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token)
                .param("username", username)
                .param("password", password))
        .andReturn();
  }

  protected AdminSession login(String username, String password, String ip) throws Exception {
    MvcResult login = attemptLogin(username, password, ip);
    assertThat(login.getResponse().getStatus())
        .as(login.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(200);
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(session).as("el login crea una sesión").isNotNull();
    return new AdminSession(session, json(login).get("csrfToken").asString());
  }
}
