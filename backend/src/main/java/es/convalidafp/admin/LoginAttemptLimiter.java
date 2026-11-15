package es.convalidafp.admin;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos fallidos de login.
 *
 * <ul>
 *   <li><b>Por usuario e IP juntos</b>: demasiados fallos de un usuario desde una IP bloquean a ese
 *       usuario solo desde esa IP. Nadie puede bloquear a un administrador desde otra IP.
 *   <li><b>Por IP</b>: demasiados fallos desde una IP, sea cual sea el usuario, bloquean esa IP.
 * </ul>
 *
 * <p>Mientras dura el bloqueo, los intentos se rechazan sin comprobar la contraseña. El tiempo se
 * cuenta desde el primer fallo. Un login correcto pone a cero el contador de ese usuario en esa IP.
 */
@Component
public class LoginAttemptLimiter {

  private final int maxFailuresPerUserAndIp;
  private final int maxFailuresPerIp;
  private final Cache<String, AtomicInteger> failuresByUserAndIp;
  private final Cache<String, AtomicInteger> failuresByIp;

  LoginAttemptLimiter(
      @Value("${convalidafp.admin.login.max-failures-per-user-and-ip}") int maxFailuresPerUserAndIp,
      @Value("${convalidafp.admin.login.max-failures-per-ip}") int maxFailuresPerIp,
      @Value("${convalidafp.admin.login.lock-minutes}") long lockMinutes) {
    this.maxFailuresPerUserAndIp = maxFailuresPerUserAndIp;
    this.maxFailuresPerIp = maxFailuresPerIp;
    Duration lock = Duration.ofMinutes(lockMinutes);
    this.failuresByUserAndIp = Caffeine.newBuilder().expireAfterWrite(lock).maximumSize(100_000).build();
    this.failuresByIp = Caffeine.newBuilder().expireAfterWrite(lock).maximumSize(100_000).build();
  }

  /** ¿Hay que rechazar este intento sin comprobar la contraseña? */
  public boolean isBlocked(String ip, String username) {
    return count(failuresByIp, ip) >= maxFailuresPerIp
        || count(failuresByUserAndIp, userAndIpKey(username, ip)) >= maxFailuresPerUserAndIp;
  }

  public void recordFailure(String ip, String username) {
    failuresByIp.get(ip, k -> new AtomicInteger()).incrementAndGet();
    failuresByUserAndIp.get(userAndIpKey(username, ip), k -> new AtomicInteger()).incrementAndGet();
  }

  public void recordSuccess(String ip, String username) {
    failuresByUserAndIp.invalidate(userAndIpKey(username, ip));
  }

  private static int count(Cache<String, AtomicInteger> cache, String key) {
    AtomicInteger failures = cache.getIfPresent(key);
    return failures == null ? 0 : failures.get();
  }

  /** El usuario se compara sin distinguir mayúsculas y con una longitud acotada. */
  private static String userAndIpKey(String username, String ip) {
    String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    return (user.length() > 100 ? user.substring(0, 100) : user) + "|" + ip;
  }
}
