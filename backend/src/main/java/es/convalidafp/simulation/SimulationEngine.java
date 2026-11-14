package es.convalidafp.simulation;

import es.convalidafp.simulation.DegreeData.SubjectData;
import es.convalidafp.simulation.RuleData.TargetType;
import es.convalidafp.simulation.SimulationResult.ConditionalRecognition;
import es.convalidafp.simulation.SimulationResult.NoAgreement;
import es.convalidafp.simulation.SimulationResult.NoData;
import es.convalidafp.simulation.SimulationResult.OptativeCredits;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.simulation.SimulationResult.RecognizedSubject;
import es.convalidafp.simulation.SimulationResult.RemainingYear;
import es.convalidafp.simulation.SimulationResult.SourceCitation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Motor de simulación: un único algoritmo genérico que evalúa las reglas de una tabla.
 *
 * <p>Es una función pura. No lee de la base de datos, no depende de Spring ni de JPA, no usa el
 * reloj y no contiene ningún caso particular de universidad: el mismo dato de entrada da siempre el
 * mismo resultado. Toda la aritmética es {@link BigDecimal}, sin redondeos.
 */
public final class SimulationEngine {

  private static final Comparator<Integer> YEAR_ORDER = Comparator.nullsLast(Comparator.naturalOrder());

  private final BigDecimal maxRecognitionRatio;

  /**
   * @param maxRecognitionRatio tope legal como fracción de la carga del grado (0.25 en la v1). Es un
   *     parámetro de configuración, no un número fijo en el código
   */
  public SimulationEngine(BigDecimal maxRecognitionRatio) {
    Objects.requireNonNull(maxRecognitionRatio, "maxRecognitionRatio");
    if (maxRecognitionRatio.signum() <= 0 || maxRecognitionRatio.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException(
          "El tope de reconocimiento debe ser mayor que 0 y como máximo 1: " + maxRecognitionRatio);
    }
    this.maxRecognitionRatio = maxRecognitionRatio;
  }

  /**
   * Simula el reconocimiento.
   *
   * @param table la tabla de ese ciclo, grado y curso; null si no hay fila (sin datos)
   * @param rules las reglas de la tabla
   * @param degree el grado de destino con sus asignaturas
   * @throws IllegalArgumentException si los datos incumplen el contrato (el importador lo impide)
   */
  public SimulationResult simulate(
      SimulationInput input, TableData table, List<RuleData> rules, DegreeData degree) {
    Objects.requireNonNull(input, "input");

    // 1. Resolver la tabla.
    if (table == null) {
      return new NoData(input);
    }
    if (table.status() == TableData.Status.SIN_CONVENIO) {
      return new NoAgreement(input, table.id(), table.source(), table.notes());
    }
    Objects.requireNonNull(rules, "rules");
    Objects.requireNonNull(degree, "degree");
    if (degree.totalEcts() == null) {
      throw new IllegalArgumentException(
          "El grado " + degree.id() + " tiene una tabla vigente y no indica su carga total");
    }
    Map<String, SubjectData> subjectsById = new LinkedHashMap<>();
    degree.subjects().forEach(s -> subjectsById.put(s.id(), s));

    // 2. Reglas aplicables: el estudiante tiene todos sus módulos. Orden estable por identificador.
    List<RuleData> applicable =
        rules.stream()
            .filter(r -> input.hasAllModules(r.requiredModuleIds()))
            .sorted(Comparator.comparing(RuleData::id))
            .toList();

    // 3 y 4. Las condicionadas van aparte; el resto se agrupa por asignatura destino.
    Map<String, List<RuleData>> rulesBySubject = new TreeMap<>();
    List<OptativeCredits> optativeCredits = new ArrayList<>();
    for (RuleData rule : applicable) {
      if (rule.conditional()) {
        continue;
      }
      if (rule.targetType() == TargetType.SUBJECT) {
        rulesBySubject.computeIfAbsent(rule.targetSubjectId(), k -> new ArrayList<>()).add(rule);
      } else {
        optativeCredits.add(new OptativeCredits(rule.id(), rule.recognizedEcts(), citation(rule, table)));
      }
    }

    List<RecognizedSubject> recognizedSubjects = new ArrayList<>();
    for (Map.Entry<String, List<RuleData>> entry : rulesBySubject.entrySet()) {
      SubjectData subject = subjectOf(entry.getKey(), subjectsById, degree, entry.getValue().get(0));
      // Cada asignatura cuenta una sola vez; se conserva como fuente la regla de menor rule_id.
      RuleData winner = entry.getValue().get(0);
      BigDecimal ects = winner.recognizedEcts() != null ? winner.recognizedEcts() : subject.ects();
      if (ects == null) {
        throw new IllegalArgumentException(
            "La asignatura " + subject.id() + " no indica ECTS y la regla " + winner.id() + " tampoco");
      }
      List<String> alternatives = entry.getValue().stream().skip(1).map(RuleData::id).toList();
      recognizedSubjects.add(
          new RecognizedSubject(
              subject.id(),
              subject.name(),
              ects,
              subject.courseYear(),
              winner.id(),
              alternatives,
              citation(winner, table)));
    }
    recognizedSubjects.sort(
        Comparator.comparing(RecognizedSubject::courseYear, YEAR_ORDER)
            .thenComparing(RecognizedSubject::subjectId));

    // Una regla condicionada no se muestra si su asignatura ya está reconocida sin condiciones.
    List<ConditionalRecognition> conditionalRecognitions = new ArrayList<>();
    for (RuleData rule : applicable) {
      if (!rule.conditional()) {
        continue;
      }
      if (rule.targetType() == TargetType.SUBJECT) {
        if (rulesBySubject.containsKey(rule.targetSubjectId())) {
          continue;
        }
        SubjectData subject = subjectOf(rule.targetSubjectId(), subjectsById, degree, rule);
        BigDecimal ects = rule.recognizedEcts() != null ? rule.recognizedEcts() : subject.ects();
        conditionalRecognitions.add(
            new ConditionalRecognition(
                rule.id(), subject.id(), subject.name(), ects, rule.conditionText(), citation(rule, table)));
      } else {
        conditionalRecognitions.add(
            new ConditionalRecognition(
                rule.id(), null, null, rule.recognizedEcts(), rule.conditionText(), citation(rule, table)));
      }
    }

    // 5. Sumar asignaturas reconocidas y créditos optativos.
    BigDecimal rulesTotal = BigDecimal.ZERO;
    for (RecognizedSubject subject : recognizedSubjects) {
      rulesTotal = rulesTotal.add(subject.ects());
    }
    for (OptativeCredits credits : optativeCredits) {
      rulesTotal = rulesTotal.add(credits.ects());
    }

    // 6 y 7. Tope: el menor entre el legal y el de la tabla. Si la suma lo supera, el total se
    // limita y se marca; el motor no descarta asignaturas.
    BigDecimal legalCap = degree.totalEcts().multiply(maxRecognitionRatio);
    BigDecimal appliedCap = table.tableMaxEcts() == null ? legalCap : legalCap.min(table.tableMaxEcts());
    boolean capped = rulesTotal.compareTo(appliedCap) > 0;
    BigDecimal recognizedEcts = capped ? appliedCap : rulesTotal;

    // 8. Asignaturas restantes por curso y ECTS pendientes.
    Map<Integer, List<SubjectData>> remaining = new TreeMap<>(YEAR_ORDER);
    for (SubjectData subject : degree.subjects()) {
      if (!rulesBySubject.containsKey(subject.id())) {
        remaining.computeIfAbsent(subject.courseYear(), k -> new ArrayList<>()).add(subject);
      }
    }
    List<RemainingYear> remainingByYear =
        remaining.entrySet().stream()
            .map(
                e ->
                    new RemainingYear(
                        e.getKey(),
                        e.getValue().stream().sorted(Comparator.comparing(SubjectData::id)).toList()))
            .toList();

    // 9. Resultado, con la fuente de cada reconocimiento y la versión del dataset (en la entrada).
    return new Recognized(
        input,
        table.id(),
        recognizedSubjects,
        optativeCredits,
        conditionalRecognitions,
        rulesTotal,
        legalCap,
        table.tableMaxEcts(),
        appliedCap,
        recognizedEcts,
        capped,
        degree.totalEcts(),
        degree.totalEcts().subtract(recognizedEcts),
        remainingByYear);
  }

  private static SubjectData subjectOf(
      String subjectId, Map<String, SubjectData> subjectsById, DegreeData degree, RuleData rule) {
    SubjectData subject = subjectsById.get(subjectId);
    if (subject == null) {
      throw new IllegalArgumentException(
          "La regla " + rule.id() + " apunta a la asignatura " + subjectId
              + ", que no pertenece al grado " + degree.id());
    }
    return subject;
  }

  /** Fuente efectiva: la propia de la regla si la tiene y, si no, la de su tabla. */
  private static SourceCitation citation(RuleData rule, TableData table) {
    SourceRef source = rule.source() != null ? rule.source() : table.source();
    return new SourceCitation(source, rule.sourcePage(), rule.sourceExcerpt());
  }
}
