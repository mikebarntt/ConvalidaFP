package es.convalidafp.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Límite de peticiones de la API pública: un número de peticiones por minuto y por IP (Bucket4j).
 * Al superarlo se responde 429 en formato Problem Details, con la cabecera {@code Retry-After}.
 *
 * <p>No se aplica a la API de administración (que exige iniciar sesión) ni a lo que no es API.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String PUBLIC_API = "/api/v1/";
  private static final String ADMIN_API = "/api/v1/admin";

  private final long requestsPerMinute;
  private final ObjectMapper json;

  /** Un cubo por IP. Las IP que dejan de llamar se olvidan a los 10 minutos. */
  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(100_000).build();

  RateLimitFilter(
      @Value("${convalidafp.api.rate-limit.requests-per-minute}") long requestsPerMinute, ObjectMapper json) {
    this.requestsPerMinute = requestsPerMinute;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith(PUBLIC_API) || path.startsWith(ADMIN_API);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Bucket bucket = buckets.get(request.getRemoteAddr(), ip -> newBucket());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      response.setHeader("X-RateLimit-Limit", Long.toString(requestsPerMinute));
      response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
      chain.doFilter(request, response);
      return;
    }
    long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", "about:blank");
    problem.put("title", ApiExceptionHandler.title(HttpStatus.TOO_MANY_REQUESTS));
    problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
    problem.put(
        "detail",
        "Has superado el límite de "
            + requestsPerMinute
            + " peticiones por minuto. Espera "
            + retryAfterSeconds
            + " segundos y vuelve a intentarlo.");
    problem.put("instance", request.getRequestURI());
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(json.writeValueAsString(problem));
  }

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(limit -> limit.capacity(requestsPerMinute).refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
        .build();
  }
}
