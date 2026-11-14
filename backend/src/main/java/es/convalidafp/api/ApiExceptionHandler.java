package es.convalidafp.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Errores de la API en formato Problem Details (RFC 9457), con mensajes en español pensados para
 * mostrarse al usuario.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  ProblemDetail notFound(ResourceNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalidRequest(InvalidRequestException ex) {
    return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  /** Cualquier error no previsto: se registra y se responde sin detalles internos. */
  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception ex) {
    log.error("Error no previsto en la API", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado. Inténtalo de nuevo más tarde.");
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body =
        problem(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio «" + ex.getParameterName() + "».");
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body = problem(HttpStatus.NOT_FOUND, "La dirección solicitada no existe.");
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /** El resto de errores de Spring MVC conservan su detalle, con el título en español. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      problem.setTitle(title(status));
    }
    return super.handleExceptionInternal(ex, body, headers, status, request);
  }

  static ProblemDetail problem(HttpStatus status, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title(status));
    return problem;
  }

  static String title(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "Petición no válida";
      case 404 -> "No encontrado";
      case 405 -> "Método no permitido";
      case 406 -> "Formato no disponible";
      case 429 -> "Demasiadas peticiones";
      default -> status.is5xxServerError() ? "Error interno" : "Error en la petición";
    };
  }
}
