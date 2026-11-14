package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Base de los tests de la API pública: la aplicación completa contra MariaDB, con el dataset de
 * ejemplo cargado y publicado al arrancar, y peticiones HTTP simuladas que pasan por el filtro de
 * límite de peticiones.
 *
 * <p>Cada test llama desde una IP distinta para no gastar el límite de 60 peticiones por minuto de
 * los demás.
 */
@SpringBootTest(
    properties = {
      "convalidafp.bootstrap.sample-dataset.enabled=true",
      "convalidafp.bootstrap.sample-dataset.dir=${convalidafp.dataDir:../data}/sample",
      "test.context=api"
    })
@Import(TestcontainersConfiguration.class)
abstract class ApiTestSupport {

  private static final AtomicInteger CLIENTS = new AtomicInteger();

  @Autowired private WebApplicationContext context;
  @Autowired private RateLimitFilter rateLimitFilter;
  @Autowired protected ObjectMapper json;

  protected MockMvc mvc;
  protected String clientIp;

  @BeforeEach
  void setUpHttpClient() {
    mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(rateLimitFilter).build();
    int n = CLIENTS.incrementAndGet();
    clientIp = "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
  }

  /** Petición GET a la API desde la IP de este test. */
  protected MockHttpServletRequestBuilder api(String url, Object... variables) {
    return get(url, variables)
        .with(
            request -> {
              request.setRemoteAddr(clientIp);
              return request;
            });
  }

  /** Hace la petición, exige un 200 y devuelve el cuerpo como JSON. */
  protected JsonNode getJson(String url, Object... variables) throws Exception {
    MvcResult result = mvc.perform(api(url, variables)).andReturn();
    assertThat(result.getResponse().getStatus())
        .as(url + " → " + result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo(200);
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  protected MvcResult perform(String url, Object... variables) throws Exception {
    return mvc.perform(api(url, variables)).andReturn();
  }

  protected JsonNode body(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
