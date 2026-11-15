package es.convalidafp.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Seguridad de la aplicación, en dos cadenas:
 *
 * <ul>
 *   <li><b>Administración</b> ({@code /api/v1/admin/**}): exige iniciar sesión con rol ADMIN. Sesión
 *       en cookie, CSRF activo y límite de intentos de login.
 *   <li><b>Todo lo demás</b> (API pública, documentación y salud): abierto, sin sesión y sin cookies.
 *       Simular no requiere cuenta.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

  static final String ADMIN_API = "/api/v1/admin/**";
  static final String LOGIN_URL = "/api/v1/admin/login";
  static final String LOGOUT_URL = "/api/v1/admin/logout";
  static final String CSRF_URL = "/api/v1/admin/csrf";

  /** bcrypt con coste 12. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Las cuentas salen solo de la tabla de administradores. Al existir este bean, Spring Boot no crea
   * su usuario de desarrollo con contraseña generada.
   */
  @Bean
  UserDetailsService userDetailsService(AdminUserRepository users) {
    return username ->
        users
            .findByUsername(username)
            .map(u -> User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles(u.getRole()).build())
            .orElseThrow(() -> new UsernameNotFoundException("No existe el administrador"));
  }

  @Bean
  @Order(1)
  SecurityFilterChain adminSecurity(
      HttpSecurity http, LoginAttemptLimiter limiter, AuditLog audit, ProblemWriter problems, ObjectMapper json)
      throws Exception {
    http.securityMatcher(ADMIN_API)
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(CSRF_URL, LOGIN_URL).permitAll().anyRequest().hasRole(AdminUser.ROLE_ADMIN))
        // El token CSRF viaja en una cookie legible por la SPA, que lo devuelve en la cabecera
        // X-XSRF-TOKEN. Protege también el login y el logout.
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .formLogin(
            login ->
                login
                    .loginProcessingUrl(LOGIN_URL)
                    .successHandler(
                        (request, response, authentication) -> {
                          limiter.recordSuccess(authentication.getName());
                          // Al iniciar sesión el token CSRF cambia: se entrega ya el nuevo.
                          CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                          Map<String, Object> body = new LinkedHashMap<>();
                          body.put("username", authentication.getName());
                          body.put("csrfToken", token == null ? null : token.getToken());
                          response.setStatus(HttpStatus.OK.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                          response.getWriter().write(json.writeValueAsString(body));
                        })
                    .failureHandler(
                        (request, response, exception) -> {
                          String username = request.getParameter("username");
                          limiter.recordFailure(request.getRemoteAddr(), username);
                          audit.recordFailedLogin(username, request.getRemoteAddr(), "Usuario o contraseña incorrectos");
                          // El mismo mensaje exista o no el usuario, para no revelar cuáles existen.
                          problems.write(
                              request, response, HttpStatus.UNAUTHORIZED, "No autenticado", "Usuario o contraseña incorrectos.");
                        }))
        .logout(
            logout ->
                logout
                    .logoutUrl(LOGOUT_URL)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value())))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            problems.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "No autenticado",
                                "Inicia sesión como administrador para acceder a esta dirección."))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            problems.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "Acceso denegado",
                                "No tienes permiso para esta operación o falta el token CSRF.")))
        .addFilterBefore(new LoginThrottleFilter(limiter, problems), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain publicSecurity(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        // Sin sesión ni cookies: la simulación es anónima y no guarda nada del visitante.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        // Solo lectura y sin sesión: no hay nada que proteger con CSRF.
        .csrf(csrf -> csrf.disable())
        // La API pública fija sus propias cabeceras de caché (ETag y Cache-Control).
        .headers(headers -> headers.cacheControl(cache -> cache.disable()));
    return http.build();
  }

  /**
   * Rechaza un intento de login, antes de comprobar la contraseña, si ese usuario o esa IP acumulan
   * demasiados fallos.
   */
  static final class LoginThrottleFilter extends OncePerRequestFilter {

    private final LoginAttemptLimiter limiter;
    private final ProblemWriter problems;

    LoginThrottleFilter(LoginAttemptLimiter limiter, ProblemWriter problems) {
      this.limiter = limiter;
      this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
      return !("POST".equals(request.getMethod()) && LOGIN_URL.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      if (limiter.isBlocked(request.getRemoteAddr(), request.getParameter("username"))) {
        response.setHeader(HttpHeaders.RETRY_AFTER, "900");
        problems.write(
            request,
            response,
            HttpStatus.TOO_MANY_REQUESTS,
            "Demasiados intentos",
            "Demasiados intentos fallidos de inicio de sesión. Espera unos minutos y vuelve a intentarlo.");
        return;
      }
      chain.doFilter(request, response);
    }
  }
}
