package es.convalidafp.simulation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Grado de destino con sus asignaturas, tal como lo necesita el motor. */
public record DegreeData(String id, String name, BigDecimal totalEcts, List<SubjectData> subjects) {

  public DegreeData {
    Objects.requireNonNull(id, "id");
    subjects = List.copyOf(subjects);
  }

  /** Asignatura de un grado. {@code ects} y {@code courseYear} pueden ser desconocidos (null). */
  public record SubjectData(
      String id, String name, BigDecimal ects, String subjectType, Integer courseYear) {

    public SubjectData {
      Objects.requireNonNull(id, "id");
    }
  }
}
