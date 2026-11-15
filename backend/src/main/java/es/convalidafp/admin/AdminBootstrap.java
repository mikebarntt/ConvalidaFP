package es.convalidafp.admin;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer administrador al arrancar, a partir de dos variables de entorno.
 *
 * <ul>
 *   <li>Solo si todavía no existe ningún administrador: nunca cambia una cuenta existente.
 *   <li>Solo si se dan el usuario y la contraseña. Sin variables no hay administrador.
 *   <li>Nunca hay una contraseña por defecto, y una contraseña corta se rechaza.
 * </ul>
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  static final int MIN_PASSWORD_LENGTH = 12;

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final AdminUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final String username;
  private final String password;

  AdminBootstrap(
      AdminUserRepository users,
      PasswordEncoder passwordEncoder,
      Clock clock,
      @Value("${convalidafp.admin.bootstrap.username:}") String username,
      @Value("${convalidafp.admin.bootstrap.password:}") String password) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.username = username == null ? "" : username.trim();
    this.password = password == null ? "" : password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.count() > 0) {
      log.info("Ya existe algún administrador: no se crea ninguno al arrancar");
      return;
    }
    if (username.isEmpty() || password.isEmpty()) {
      log.warn(
          "No hay ningún administrador y no se han definido las variables del primer administrador:"
              + " el panel de administración no es accesible");
      return;
    }
    if (password.length() < MIN_PASSWORD_LENGTH) {
      log.error(
          "No se crea el primer administrador: la contraseña debe tener al menos {} caracteres",
          MIN_PASSWORD_LENGTH);
      return;
    }
    users.save(new AdminUser(username, passwordEncoder.encode(password), clock.instant()));
    log.info("Creado el primer administrador «{}»", username);
  }
}
