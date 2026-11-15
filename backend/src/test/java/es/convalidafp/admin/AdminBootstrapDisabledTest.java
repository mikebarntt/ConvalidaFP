package es.convalidafp.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sin variables de entorno no hay administrador: la aplicación arranca con la configuración por
 * defecto y sin ninguna cuenta. Nunca hay una contraseña por defecto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminBootstrapDisabledTest {

  @Autowired private AdminUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private Clock clock;
  @Autowired private Environment environment;
  @Autowired private UserDetailsService userDetailsService;

  @Test
  @DisplayName("Por defecto no hay credenciales configuradas ni ningún administrador")
  void withoutEnvironmentVariablesThereIsNoAdmin() {
    assertThat(environment.getProperty("convalidafp.admin.bootstrap.username")).isEmpty();
    assertThat(environment.getProperty("convalidafp.admin.bootstrap.password")).isEmpty();
    assertThat(users.count()).isZero();
    // Tampoco existe el usuario de desarrollo que Spring Boot crea cuando nadie define las cuentas.
    assertThat(environment.getProperty("spring.security.user.password")).isNull();
    for (String username : new String[] {"user", "admin"}) {
      assertThatThrownBy(() -> userDetailsService.loadUserByUsername(username))
          .isInstanceOf(UsernameNotFoundException.class);
    }
  }

  @Test
  @DisplayName("Hacen falta las dos variables: con solo el usuario o solo la contraseña no se crea nada")
  void bothVariablesAreRequired() {
    bootstrap("solo-usuario", "").run(null);
    bootstrap("", "solo-una-clave-larga-de-prueba").run(null);
    bootstrap("   ", "solo-una-clave-larga-de-prueba").run(null);
    bootstrap(null, null).run(null);

    assertThat(users.count()).isZero();
  }

  @Test
  @DisplayName("Una contraseña de menos de 12 caracteres se rechaza")
  void shortPasswordIsRejected() {
    bootstrap("admin-de-prueba", "corta").run(null);
    bootstrap("admin-de-prueba", "once-letras").run(null);

    assertThat(users.count()).isZero();
  }

  @Test
  @DisplayName("Con usuario y contraseña válidos, y ninguna cuenta previa, se crea el administrador")
  void validVariablesCreateTheFirstAdmin() {
    bootstrap(" admin-de-prueba ", "doce-letras!").run(null);

    assertThat(users.count()).isEqualTo(1);
    AdminUser admin = users.findByUsername("admin-de-prueba").orElseThrow();
    assertThat(passwordEncoder.matches("doce-letras!", admin.getPasswordHash())).isTrue();
    assertThat(userDetailsService.loadUserByUsername("admin-de-prueba").getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_ADMIN");
  }

  private AdminBootstrap bootstrap(String username, String password) {
    return new AdminBootstrap(users, passwordEncoder, clock, username, password);
  }
}
