package es.convalidafp.simulation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Tabla de reconocimiento de un ciclo frente a un grado en un curso.
 *
 * <p>{@code tableMaxEcts} es el tope propio de la tabla (null si no tiene): solo puede bajar el
 * límite. {@code source} es la fuente por defecto de sus reglas.
 */
public record TableData(
    String id, Status status, SourceRef source, BigDecimal tableMaxEcts, String notes) {

  /** VIGENTE = hay tabla firmada; SIN_CONVENIO = verificado que no la hay. */
  public enum Status {
    VIGENTE,
    SIN_CONVENIO
  }

  public TableData {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
  }
}
