package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;

/**
 * Contrapartida de {@link UntrustedProxyTest}: si la petición llega de un proxy de confianza, la IP
 * del cliente se toma de X-Forwarded-For y cada IP tiene su propio límite.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
      "convalidafp.api.rate-limit.requests-per-minute=3"
    })
@Import(TestcontainersConfiguration.class)
class TrustedProxyTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  @DisplayName("X-Forwarded-For de un proxy de confianza se usa como IP del cliente")
  void forwardedForFromTrustedProxyIsUsed() throws Exception {
    assertThat(ForwardedForSupport.statusesWithChangingForwardedFor(port, 5)).doesNotContain(429);
  }
}
