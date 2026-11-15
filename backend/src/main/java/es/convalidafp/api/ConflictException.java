package es.convalidafp.api;

/**
 * 409: la operación no se puede hacer en el estado actual (por ejemplo, publicar algo que ya está
 * publicado). El mensaje se muestra al usuario.
 */
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String message) {
    super(message);
  }
}
