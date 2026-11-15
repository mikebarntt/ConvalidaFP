package es.convalidafp.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import es.convalidafp.admin.AuditEvent.Action;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

/** Login de administrador con sesión y CSRF, y límite de intentos fallidos por usuario y por IP. */
class AdminLoginTest extends AdminTestSupport {

  @Autowired private AdminUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private AuditEventRepository auditEvents;

  @Test
  @DisplayName("Login correcto: crea una sesión, y con ella se accede a la API de admin")
  void successfulLoginOpensASession() throws Exception {
    AdminSession session = login(ADMIN, PASSWORD, clientIp);

    MvcResult who = mvc.perform(session.apply(get("/api/v1/admin/session"))).andReturn();
    assertThat(who.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(who).get("username").asString()).isEqualTo(ADMIN);
    assertThat(mvc.perform(session.apply(get("/api/v1/admin/audit"))).andReturn().getResponse().getStatus())
        .isEqualTo(200);

    // Sin la sesión, la misma petición no pasa.
    assertThat(mvc.perform(get("/api/v1/admin/session")).andReturn().getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("Con sesión iniciada, un POST necesita además el token CSRF")
  void sessionAlonIsNotEnoughForPosts() throws Exception {
    AdminSession session = login(ADMIN, PASSWORD, clientIp);

    MockHttpServletResponse withoutToken =
        mvc.perform(session.withoutCsrf(post("/api/v1/admin/imports/999999/publish"))).andReturn().getResponse();
    assertProblem(withoutToken, 403, "token CSRF");

    // Con el token, la petición llega al controlador (que responde 404: esa importación no existe).
    MockHttpServletResponse withToken =
        mvc.perform(session.apply(post("/api/v1/admin/imports/999999/publish"))).andReturn().getResponse();
    assertProblem(withToken, 404, "No existe la importación 999999");
  }

  @Test
  @DisplayName("El login también está protegido por CSRF")
  void loginRequiresCsrfToken() throws Exception {
    MockHttpServletResponse response =
        mvc.perform(post("/api/v1/admin/login").with(from(clientIp)).param("username", ADMIN).param("password", PASSWORD))
            .andReturn()
            .getResponse();
    assertProblem(response, 403, "token CSRF");
  }

  @Test
  @DisplayName("Contraseña incorrecta o usuario inexistente: 401 con el mismo mensaje, y queda en la auditoría")
  void failedLoginIsRejectedAndAudited() throws Exception {
    Instant before = Instant.now().minusSeconds(1);

    MvcResult wrongPassword = attemptLogin(ADMIN, "no-es-la-clave", clientIp);
    assertProblem(wrongPassword.getResponse(), 401, "Usuario o contraseña incorrectos");
    assertThat(wrongPassword.getRequest().getSession(false)).isNull();

    String ghostIp = newIp();
    MvcResult unknownUser = attemptLogin("fantasma-" + ghostIp, PASSWORD, ghostIp);
    assertProblem(unknownUser.getResponse(), 401, "Usuario o contraseña incorrectos");

    List<AuditEvent> failures = auditEvents.findByActionOrderByIdDesc(Action.LOGIN_FALLIDO);
    AuditEvent ofAdmin = failures.stream().filter(e -> clientIp.equals(e.getIp())).findFirst().orElseThrow();
    assertThat(ofAdmin.getActor()).isEqualTo(ADMIN);
    assertThat(ofAdmin.getOccurredAt()).isAfter(before);
    assertThat(ofAdmin.getDatasetVersionId()).isNull();
    assertThat(failures).anyMatch(e -> ghostIp.equals(e.getIp()) && e.getActor().equals("fantasma-" + ghostIp));
    // La contraseña probada no se guarda en ningún sitio.
    assertThat(failures).noneMatch(e -> e.getDetail() != null && e.getDetail().contains("no-es-la-clave"));

    // Un fallo aislado no bloquea: el administrador entra a continuación.
    login(ADMIN, PASSWORD, newIp());
  }

  @Test
  @DisplayName("No existe ningún usuario por defecto")
  void thereIsNoDefaultUser() throws Exception {
    for (String username : new String[] {"user", "admin", "root"}) {
      MvcResult attempt = attemptLogin(username, "password", newIp());
      assertThat(attempt.getResponse().getStatus()).as(username).isEqualTo(401);
    }
    assertThat(users.findAll()).extracting(AdminUser::getUsername).doesNotContain("user", "admin", "root");
  }

  @Test
  @DisplayName("Límite por usuario: tras 5 fallos, ni la contraseña correcta entra, venga de la IP que venga")
  void tooManyFailuresLockTheUser() throws Exception {
    String username = "admin-bloqueado";
    String password = "otra-clave-de-prueba-9876543210";
    users.save(new AdminUser(username, passwordEncoder.encode(password), Instant.now()));

    for (int i = 1; i <= 5; i++) {
      // Cada fallo llega desde una IP distinta: el límite es del usuario, no de la IP.
      assertThat(attemptLogin(username, "clave-mala-" + i, newIp()).getResponse().getStatus()).as("fallo " + i).isEqualTo(401);
    }

    MockHttpServletResponse locked = attemptLogin(username, password, newIp()).getResponse();
    assertProblem(locked, 429, "Demasiados intentos fallidos");
    assertThat(locked.getHeader("Retry-After")).isEqualTo("900");
    // El usuario se compara sin distinguir mayúsculas.
    assertThat(attemptLogin(username.toUpperCase(), password, newIp()).getResponse().getStatus()).isEqualTo(429);
    // Los demás administradores no se ven afectados.
    login(ADMIN, PASSWORD, newIp());
  }

  @Test
  @DisplayName("Límite por IP: tras 20 fallos desde una IP, esa IP no puede intentarlo ni con otro usuario")
  void tooManyFailuresLockTheIp() throws Exception {
    String attackerIp = newIp();
    for (int i = 1; i <= 20; i++) {
      assertThat(attemptLogin("usuario-inventado-" + i, "clave-mala", attackerIp).getResponse().getStatus())
          .as("fallo " + i)
          .isEqualTo(401);
    }

    assertProblem(attemptLogin(ADMIN, PASSWORD, attackerIp).getResponse(), 429, "Demasiados intentos fallidos");
    // Desde otra IP, el administrador entra con normalidad.
    login(ADMIN, PASSWORD, newIp());
  }

  @Test
  @DisplayName("Logout: cierra la sesión")
  void logoutEndsTheSession() throws Exception {
    AdminSession session = login(ADMIN, PASSWORD, clientIp);

    int status = mvc.perform(session.apply(post("/api/v1/admin/logout"))).andReturn().getResponse().getStatus();
    assertThat(status).isEqualTo(204);
    assertThat(session.session().isInvalid()).isTrue();
  }
}
