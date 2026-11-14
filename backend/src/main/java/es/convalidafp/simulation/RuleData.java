package es.convalidafp.simulation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Regla de reconocimiento, como dato. El motor no contiene ningún caso particular: solo evalúa
 * reglas como esta.
 *
 * <ul>
 *   <li>{@code requiredModuleIds}: todos son obligatorios (Y). Vacío = se aplica siempre.
 *   <li>{@code recognizedEcts}: en una regla SUBJECT, null = los ECTS de la asignatura.
 *   <li>{@code source}: fuente propia de la regla; null = hereda la de su tabla.
 * </ul>
 */
public record RuleData(
    String id,
    TargetType targetType,
    String targetSubjectId,
    BigDecimal recognizedEcts,
    boolean conditional,
    String conditionText,
    SourceRef source,
    int sourcePage,
    String sourceExcerpt,
    Set<String> requiredModuleIds) {

  /** Qué reconoce la regla. */
  public enum TargetType {
    SUBJECT,
    OPTATIVE_CREDITS
  }

  public RuleData {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(targetType, "targetType");
    requiredModuleIds = Set.copyOf(requiredModuleIds);
    if (targetType == TargetType.SUBJECT && targetSubjectId == null) {
      throw new IllegalArgumentException("La regla " + id + " es SUBJECT y no tiene asignatura destino");
    }
    if (targetType == TargetType.OPTATIVE_CREDITS && recognizedEcts == null) {
      throw new IllegalArgumentException("La regla " + id + " es OPTATIVE_CREDITS y no indica créditos");
    }
  }
}
