package es.convalidafp.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Con las variables de entorno definidas, el primer administrador se crea al arrancar. */
class AdminBootstrapTest extends AdminTestSupport {

  @Autowired private AdminUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private Clock clock;

  @Test
  @DisplayName("Se crea el primer administrador, con la contraseña guardada solo como hash bcrypt")
  void firstAdminIsCreatedFromTheEnvironment() {
    AdminUser admin = users.findByUsername(ADMIN).orElseThrow();

    assertThat(admin.getRole()).isEqualTo("ADMIN");
    assertThat(admin.getCreatedAt()).isNotNull();
    assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
    assertThat(admin.getPasswordHash()).matches("\\$2[aby]\\$12\\$.{53}");
    assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
    assertThat(passwordEncoder.matches("otra-clave-cualquiera", admin.getPasswordHash())).isFalse();
  }

  @Test
  @DisplayName("Si ya existe un administrador, arrancar con otras variables no crea ni cambia nada")
  void existingAdminIsNeverTouched() {
    long before = users.count();
    String hashBefore = users.findByUsername(ADMIN).orElseThrow().getPasswordHash();

    new AdminBootstrap(users, passwordEncoder, clock, "otro-admin", "otra-clave-larga-de-prueba").run(null);
    new AdminBootstrap(users, passwordEncoder, clock, ADMIN, "clave-nueva-larga-de-prueba").run(null);

    assertThat(users.count()).isEqualTo(before);
    assertThat(users.findByUsername("otro-admin")).isEmpty();
    assertThat(users.findByUsername(ADMIN).orElseThrow().getPasswordHash()).isEqualTo(hashBefore);
  }
}
