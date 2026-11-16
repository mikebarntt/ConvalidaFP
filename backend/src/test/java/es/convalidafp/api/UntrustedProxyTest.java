package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;

/**
 * Producción (docker-compose.prod.yml): el backend solo acepta X-Forwarded-For de los proxies de
 * confianza (server.tomcat.remoteip.internal-proxies). Aquí 127.0.0.1 no es de confianza, así que
 * cambiar la cabecera no sirve para saltarse el límite de peticiones por IP.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "server.tomcat.remoteip.internal-proxies=172\\.30\\.0\\.(10|20)",
      "convalidafp.api.rate-limit.requests-per-minute=3"
    })
@Import(TestcontainersConfiguration.class)
class UntrustedProxyTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  @DisplayName("X-Forwarded-For de un cliente que no es proxy de confianza se ignora: el límite se aplica")
  void forwardedForFromUntrustedClientIsIgnored() throws Exception {
    assertThat(ForwardedForSupport.statusesWithChangingForwardedFor(port, 5)).endsWith(429, 429);
  }
}
