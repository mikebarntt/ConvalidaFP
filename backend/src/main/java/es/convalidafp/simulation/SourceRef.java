package es.convalidafp.simulation;

import java.time.LocalDate;
import java.util.Objects;

/** Documento oficial del que sale una tabla o una regla: tipo, título, fecha y URL. */
public record SourceRef(
    String id, String documentType, String title, LocalDate publicationDate, String url) {

  public SourceRef {
    Objects.requireNonNull(id, "id");
  }
}
