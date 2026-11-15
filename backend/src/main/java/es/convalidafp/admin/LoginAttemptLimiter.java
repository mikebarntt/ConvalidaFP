package es.convalidafp.admin;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos fallidos de login, por usuario y por IP.
 *
 * <p>Tras demasiados fallos, los nuevos intentos se rechazan sin comprobar la contraseña hasta que
 * pase el tiempo de bloqueo, contado desde el primer fallo. Un login correcto pone a cero el
 * contador de ese usuario.
 */
@Component
public class LoginAttemptLimiter {

  private final int maxFailuresPerUser;
  private final int maxFailuresPerIp;
  private final Cache<String, AtomicInteger> failuresByUser;
  private final Cache<String, AtomicInteger> failuresByIp;

  LoginAttemptLimiter(
      @Value("${convalidafp.admin.login.max-failures-per-user}") int maxFailuresPerUser,
      @Value("${convalidafp.admin.login.max-failures-per-ip}") int maxFailuresPerIp,
      @Value("${convalidafp.admin.login.lock-minutes}") long lockMinutes) {
    this.maxFailuresPerUser = maxFailuresPerUser;
    this.maxFailuresPerIp = maxFailuresPerIp;
    Duration lock = Duration.ofMinutes(lockMinutes);
    this.failuresByUser = Caffeine.newBuilder().expireAfterWrite(lock).maximumSize(100_000).build();
    this.failuresByIp = Caffeine.newBuilder().expireAfterWrite(lock).maximumSize(100_000).build();
  }

  /** ¿Hay que rechazar este intento sin comprobar la contraseña? */
  public boolean isBlocked(String ip, String username) {
    return count(failuresByIp, ip) >= maxFailuresPerIp || count(failuresByUser, userKey(username)) >= maxFailuresPerUser;
  }

  public void recordFailure(String ip, String username) {
    failuresByIp.get(ip, k -> new AtomicInteger()).incrementAndGet();
    failuresByUser.get(userKey(username), k -> new AtomicInteger()).incrementAndGet();
  }

  public void recordSuccess(String username) {
    failuresByUser.invalidate(userKey(username));
  }

  private static int count(Cache<String, AtomicInteger> cache, String key) {
    AtomicInteger failures = cache.getIfPresent(key);
    return failures == null ? 0 : failures.get();
  }

  /** El usuario se compara sin distinguir mayúsculas y con una longitud acotada. */
  private static String userKey(String username) {
    String key = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    return key.length() > 100 ? key.substring(0, 100) : key;
  }
}
