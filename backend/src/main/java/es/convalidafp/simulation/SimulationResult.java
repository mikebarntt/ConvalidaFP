package es.convalidafp.simulation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de una simulación. Hay tres resultados posibles y los tres son válidos:
 *
 * <ul>
 *   <li>{@link Recognized}: existe tabla vigente y se ha calculado el reconocimiento.
 *   <li>{@link NoAgreement}: se ha verificado que no existe tabla firmada.
 *   <li>{@link NoData}: no hay datos para esa combinación (no se ha comprobado).
 * </ul>
 */
public sealed interface SimulationResult {

  /** La entrada que produjo este resultado, incluida la versión del dataset usada. */
  SimulationInput input();

  /** Sin fila en las tablas de reconocimiento: no se sabe si existe convenio. */
  record NoData(SimulationInput input) implements SimulationResult {}

  /** Verificado que no existe tabla firmada. {@code source} puede ser null. */
  record NoAgreement(SimulationInput input, String tableId, SourceRef source, String notes)
      implements SimulationResult {}

  /**
   * Reconocimiento calculado.
   *
   * @param recognizedSubjects asignaturas reconocidas, por curso; cada una cuenta una sola vez
   * @param optativeCredits créditos optativos sin asignatura concreta
   * @param conditionalRecognitions reglas condicionadas: se muestran aparte y no suman
   * @param rulesTotalEcts suma de asignaturas reconocidas y créditos optativos, antes del tope
   * @param legalCapEcts tope legal: la fracción configurada (25 %) de la carga del grado
   * @param tableCapEcts tope propio de la tabla; null si no tiene
   * @param appliedCapEcts el menor de los topes
   * @param recognizedEcts total reconocido: el menor entre la suma y el tope aplicado
   * @param capped true si la suma supera el tope. El motor no descarta asignaturas: la
   *     universidad decide cuáles entran hasta el límite
   * @param degreeTotalEcts carga total del grado
   * @param pendingEcts ECTS que quedan por cursar: carga total menos total reconocido
   * @param remainingByYear asignaturas del grado no reconocidas, agrupadas por curso
   */
  record Recognized(
      SimulationInput input,
      String tableId,
      List<RecognizedSubject> recognizedSubjects,
      List<OptativeCredits> optativeCredits,
      List<ConditionalRecognition> conditionalRecognitions,
      BigDecimal rulesTotalEcts,
      BigDecimal legalCapEcts,
      BigDecimal tableCapEcts,
      BigDecimal appliedCapEcts,
      BigDecimal recognizedEcts,
      boolean capped,
      BigDecimal degreeTotalEcts,
      BigDecimal pendingEcts,
      List<RemainingYear> remainingByYear)
      implements SimulationResult {

    public Recognized {
      recognizedSubjects = List.copyOf(recognizedSubjects);
      optativeCredits = List.copyOf(optativeCredits);
      conditionalRecognitions = List.copyOf(conditionalRecognitions);
      remainingByYear = List.copyOf(remainingByYear);
    }
  }

  /** De qué documento, página y fragmento sale un reconocimiento. */
  record SourceCitation(SourceRef source, int page, String excerpt) {}

  /**
   * Asignatura reconocida.
   *
   * @param ruleId regla que se conserva como fuente: la de menor identificador
   * @param alternativeRuleIds otras reglas aplicables que llevan a la misma asignatura
   */
  record RecognizedSubject(
      String subjectId,
      String name,
      BigDecimal ects,
      Integer courseYear,
      String ruleId,
      List<String> alternativeRuleIds,
      SourceCitation citation) {

    public RecognizedSubject {
      alternativeRuleIds = List.copyOf(alternativeRuleIds);
    }
  }

  /** Créditos optativos reconocidos sin asignatura concreta. */
  record OptativeCredits(String ruleId, BigDecimal ects, SourceCitation citation) {}

  /**
   * Reconocimiento condicionado: no suma al total.
   *
   * @param subjectId asignatura destino; null si la regla es de créditos optativos
   * @param ects ECTS que se reconocerían; null si no se conocen
   * @param conditionText la condición; null si el documento no la especifica
   */
  record ConditionalRecognition(
      String ruleId,
      String subjectId,
      String subjectName,
      BigDecimal ects,
      String conditionText,
      SourceCitation citation) {}

  /** Asignaturas no reconocidas de un curso. {@code courseYear} null = curso desconocido. */
  record RemainingYear(Integer courseYear, List<DegreeData.SubjectData> subjects) {

    public RemainingYear {
      subjects = List.copyOf(subjects);
    }
  }
}
