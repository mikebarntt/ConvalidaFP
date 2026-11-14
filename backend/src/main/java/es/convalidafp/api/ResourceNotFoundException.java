package es.convalidafp.api;

/** 404: lo que se pide no existe en los datos publicados. El mensaje se muestra al usuario. */
public class ResourceNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
