package es.convalidafp.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Peticiones HTTP reales (contra Tomcat, no MockMvc) desde 127.0.0.1, cada una con una cabecera
 * X-Forwarded-For distinta. Sirve para comprobar de qué proxies acepta el backend esa cabecera:
 * el límite de peticiones por IP solo se puede saltar si se acepta de cualquiera.
 */
final class ForwardedForSupport {

  private ForwardedForSupport() {}

  /** Estados HTTP de {@code count} peticiones a la API pública, cada una «desde» una IP distinta. */
  static List<Integer> statusesWithChangingForwardedFor(int port, int count) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    List<Integer> statuses = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/cycles"))
              .header("X-Forwarded-For", "198.51.100." + i)
              .GET()
              .build();
      statuses.add(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
    }
    return statuses;
  }
}
