package es.convalidafp.api;

/** 400: la petición no es válida. El mensaje se muestra al usuario. */
public class InvalidRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidRequestException(String message) {
    super(message);
  }
}
