package es.convalidafp.simulation;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Entrada de una simulación: ciclo, grado y curso, dentro de una versión concreta del dataset.
 *
 * <p>{@code passedModuleIds} son los módulos que el estudiante tiene superados. {@code null}
 * significa que tiene el título, es decir, todos los módulos del ciclo: es lo que asume la v1.
 */
public record SimulationInput(
    String cycleId,
    String degreeId,
    String academicYear,
    Long datasetVersionId,
    Set<String> passedModuleIds) {

  public SimulationInput {
    Objects.requireNonNull(cycleId, "cycleId");
    Objects.requireNonNull(degreeId, "degreeId");
    Objects.requireNonNull(academicYear, "academicYear");
    passedModuleIds = passedModuleIds == null ? null : Set.copyOf(passedModuleIds);
  }

  /** El estudiante tiene el título: todos los módulos del ciclo superados. */
  public static SimulationInput withFullTitle(
      String cycleId, String degreeId, String academicYear, Long datasetVersionId) {
    return new SimulationInput(cycleId, degreeId, academicYear, datasetVersionId, null);
  }

  /** El estudiante solo tiene superados los módulos indicados. */
  public static SimulationInput withModules(
      String cycleId,
      String degreeId,
      String academicYear,
      Long datasetVersionId,
      Set<String> passedModuleIds) {
    return new SimulationInput(
        cycleId, degreeId, academicYear, datasetVersionId, Objects.requireNonNull(passedModuleIds));
  }

  public boolean hasFullTitle() {
    return passedModuleIds == null;
  }

  /** ¿Tiene el estudiante todos estos módulos? Con una lista vacía, siempre sí. */
  public boolean hasAllModules(Collection<String> moduleIds) {
    return hasFullTitle() || passedModuleIds.containsAll(moduleIds);
  }
}
