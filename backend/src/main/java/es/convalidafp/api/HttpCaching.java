package es.convalidafp.api;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/**
 * Cabeceras de caché HTTP de la API pública: {@code ETag} ligado a la versión publicada y {@code
 * Cache-Control}. Si el cliente ya tiene la respuesta de esa versión ({@code If-None-Match}), se
 * responde 304 sin cuerpo.
 */
@Component
class HttpCaching {

  private final CacheControl cacheControl;

  HttpCaching(@Value("${convalidafp.api.cache-max-age-seconds}") long maxAgeSeconds) {
    this.cacheControl = CacheControl.maxAge(Duration.ofSeconds(maxAgeSeconds)).cachePublic();
  }

  <T> ResponseEntity<T> respond(WebRequest request, PublishedDataset dataset, T body) {
    String etag = dataset.etag();
    if (request.checkNotModified(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cacheControl).build();
    }
    return ResponseEntity.ok().eTag(etag).cacheControl(cacheControl).body(body);
  }
}
