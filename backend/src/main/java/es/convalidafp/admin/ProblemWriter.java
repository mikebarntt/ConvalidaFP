package es.convalidafp.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe errores en formato Problem Details (RFC 9457) desde los filtros de seguridad, que se
 * ejecutan antes de llegar a los controladores.
 */
@Component
class ProblemWriter {

  private final ObjectMapper json;

  ProblemWriter(ObjectMapper json) {
    this.json = json;
  }

  void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title, String detail)
      throws IOException {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("type", "about:blank");
    problem.put("title", title);
    problem.put("status", status.value());
    problem.put("detail", detail);
    problem.put("instance", request.getRequestURI());
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(json.writeValueAsString(problem));
  }
}
