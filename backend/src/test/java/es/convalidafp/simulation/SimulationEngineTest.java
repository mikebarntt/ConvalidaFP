package es.convalidafp.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.convalidafp.simulation.DegreeData.SubjectData;
import es.convalidafp.simulation.RuleData.TargetType;
import es.convalidafp.simulation.SimulationResult.ConditionalRecognition;
import es.convalidafp.simulation.SimulationResult.NoAgreement;
import es.convalidafp.simulation.SimulationResult.NoData;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.simulation.SimulationResult.RecognizedSubject;
import es.convalidafp.simulation.SimulationResult.RemainingYear;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El motor, probado como función pura: sin Spring y sin base de datos. Hay un test por cada caso
 * límite de la especificación, con datos mínimos y ficticios.
 */
class SimulationEngineTest {

  private static final SourceRef TABLE_SOURCE =
      new SourceRef("S-EJ-01", "CONVENIO", "Convenio Ficticio", LocalDate.of(2025, 3, 14), "https://uej.example/c.pdf");
  private static final SourceRef ADDENDUM =
      new SourceRef("S-EJ-04", "ADENDA", "Adenda Ficticia", LocalDate.of(2026, 7, 15), "https://uej.example/a.pdf");
  private static final TableData TABLE = new TableData("T-EJ-01", TableData.Status.VIGENTE, TABLE_SOURCE, null, null);

  private final SimulationEngine engine = new SimulationEngine(new BigDecimal("0.25"));

  // ---------- los 15 casos límite ----------

  @Test
  @DisplayName("Caso 1: sin fila es NO_DATA y SIN_CONVENIO es NO_AGREEMENT; son resultados distintos")
  void case01NoDataVersusNoAgreement() {
    SimulationInput input = title();
    TableData noAgreement =
        new TableData("T-FI-01", TableData.Status.SIN_CONVENIO, TABLE_SOURCE, null, "Verificado: no existe tabla");

    SimulationResult withoutRow = engine.simulate(input, null, List.of(), degree("240"));
    SimulationResult verified = engine.simulate(input, noAgreement, List.of(), degree("240"));

    assertThat(withoutRow).isEqualTo(new NoData(input));
    assertThat(verified).isEqualTo(new NoAgreement(input, "T-FI-01", TABLE_SOURCE, "Verificado: no existe tabla"));
    assertThat(withoutRow).isNotEqualTo(verified);
    // No hacen falta ni reglas ni grado para estos dos resultados.
    assertThat(engine.simulate(input, null, null, null)).isInstanceOf(NoData.class);
    assertThat(engine.simulate(input, noAgreement, null, null)).isInstanceOf(NoAgreement.class);
  }

  @Test
  @DisplayName("Caso 2: una asignatura que exige dos módulos (Y) solo se reconoce con ambos")
  void case02SubjectRequiringTwoModules() {
    DegreeData degree = degree("240", subject("BD", "6", 2));
    List<RuleData> rules = List.of(rule("R-002").subject("BD").requires("M02", "M03").build());

    assertThat(recognized(modules("M02"), TABLE, rules, degree).recognizedSubjects()).isEmpty();
    assertThat(recognized(modules("M03"), TABLE, rules, degree).recognizedSubjects()).isEmpty();
    assertThat(recognized(modules("M02", "M03"), TABLE, rules, degree).recognizedEcts()).isEqualByComparingTo("6");
    assertThat(recognized(title(), TABLE, rules, degree).recognizedEcts()).isEqualByComparingTo("6");
  }

  @Test
  @DisplayName("Caso 3: una asignatura alcanzable por dos reglas (O) cuenta una vez; fuente = menor rule_id")
  void case03SameSubjectReachableByTwoRules() {
    DegreeData degree = degree("240", subject("WEB", "6", 2));
    List<RuleData> rules =
        List.of(
            rule("R-007").subject("WEB").requires("M06", "M08").page(9).build(),
            rule("R-006").subject("WEB").requires("M07").page(3).build());

    Recognized both = recognized(title(), TABLE, rules, degree);
    assertThat(both.recognizedSubjects()).hasSize(1);
    assertThat(both.recognizedEcts()).isEqualByComparingTo("6");
    RecognizedSubject web = both.recognizedSubjects().get(0);
    assertThat(web.ruleId()).isEqualTo("R-006");
    assertThat(web.alternativeRuleIds()).containsExactly("R-007");
    assertThat(web.citation().page()).isEqualTo(3);

    // Con solo los módulos de la segunda regla, la asignatura se reconoce por ella.
    Recognized second = recognized(modules("M06", "M08"), TABLE, rules, degree);
    assertThat(second.recognizedSubjects().get(0).ruleId()).isEqualTo("R-007");
    assertThat(second.recognizedSubjects().get(0).alternativeRuleIds()).isEmpty();
  }

  @Test
  @DisplayName("Caso 4: un módulo que sirve para varias asignaturas las reconoce todas")
  void case04OneModuleServesSeveralSubjects() {
    DegreeData degree = degree("240", subject("PROG1", "6", 1), subject("PROG2", "6", 1));
    List<RuleData> rules =
        List.of(rule("R-001").subject("PROG1").requires("M01").build(), rule("R-005").subject("PROG2").requires("M01").build());

    Recognized result = recognized(modules("M01"), TABLE, rules, degree);
    assertThat(result.recognizedSubjects()).extracting(RecognizedSubject::subjectId).containsExactly("PROG1", "PROG2");
    assertThat(result.recognizedEcts()).isEqualByComparingTo("12");
  }

  @Test
  @DisplayName("Caso 5: si la suma supera el 25 %, el total se limita y no se descarta ninguna asignatura")
  void case05SumAboveLegalCap() {
    List<SubjectData> subjects = new ArrayList<>();
    List<RuleData> rules = new ArrayList<>();
    for (int i = 1; i <= 11; i++) {
      subjects.add(subject("S" + pad(i), "6", 1));
      rules.add(rule("R-" + pad(i)).subject("S" + pad(i)).requires("M" + pad(i)).build());
    }
    DegreeData degree = new DegreeData("UPR-GD", "Grado de Demostración", new BigDecimal("240"), subjects);

    Recognized result = recognized(title(), TABLE, rules, degree);
    assertThat(result.rulesTotalEcts()).isEqualByComparingTo("66");
    assertThat(result.legalCapEcts()).isEqualByComparingTo("60");
    assertThat(result.recognizedEcts()).isEqualByComparingTo("60");
    assertThat(result.capped()).isTrue();
    assertThat(result.recognizedSubjects()).hasSize(11);
    assertThat(result.pendingEcts()).isEqualByComparingTo("180");
  }

  @Test
  @DisplayName("Caso 5 (frontera): una suma igual al tope no se marca como limitada")
  void case05SumEqualToCapIsNotCapped() {
    DegreeData degree = degree("24", subject("A", "6", 1));
    Recognized result = recognized(title(), TABLE, List.of(rule("R-001").subject("A").build()), degree);
    assertThat(result.recognizedEcts()).isEqualByComparingTo("6");
    assertThat(result.capped()).isFalse();
  }

  @Test
  @DisplayName("Caso 6: si el tope de la tabla es inferior al 25 %, se aplica el menor")
  void case06TableCapBelowLegalCap() {
    DegreeData degree = degree("240", subject("A", "18", 1), subject("B", "18", 1));
    List<RuleData> rules = List.of(rule("R-001").subject("A").build(), rule("R-002").subject("B").build());
    TableData capped30 = new TableData("T-PR-02", TableData.Status.VIGENTE, TABLE_SOURCE, new BigDecimal("30"), null);
    TableData capped90 = new TableData("T-PR-03", TableData.Status.VIGENTE, TABLE_SOURCE, new BigDecimal("90"), null);

    Recognized lower = recognized(title(), capped30, rules, degree);
    assertThat(lower.tableCapEcts()).isEqualByComparingTo("30");
    assertThat(lower.appliedCapEcts()).isEqualByComparingTo("30");
    assertThat(lower.recognizedEcts()).isEqualByComparingTo("30");
    assertThat(lower.capped()).isTrue();

    // Un tope de tabla superior al 25 % no sube el límite legal.
    Recognized higher = recognized(title(), capped90, rules, degree);
    assertThat(higher.appliedCapEcts()).isEqualByComparingTo("60");
    assertThat(higher.recognizedEcts()).isEqualByComparingTo("36");
    assertThat(higher.capped()).isFalse();
  }

  @Test
  @DisplayName("Caso 7: créditos optativos genéricos sin asignatura; varias reglas se suman")
  void case07GenericOptativeCredits() {
    DegreeData degree = degree("240", subject("A", "6", 1));
    List<RuleData> rules =
        List.of(
            rule("R-003").optative("6").requires("M04").build(),
            rule("R-028").optative("4.5").requires("M07").build(),
            rule("R-001").subject("A").requires("M01").build());

    Recognized result = recognized(title(), TABLE, rules, degree);
    assertThat(result.optativeCredits()).extracting(c -> c.ruleId()).containsExactly("R-003", "R-028");
    assertThat(result.rulesTotalEcts()).isEqualByComparingTo("16.5");
    assertThat(result.recognizedSubjects()).hasSize(1);
    // Los créditos optativos no quitan ninguna asignatura de las restantes.
    assertThat(result.remainingByYear()).isEmpty();
  }

  @Test
  @DisplayName("Caso 8: una regla condicionada aparece aparte y no suma")
  void case08ConditionalRuleDoesNotAdd() {
    DegreeData degree = degree("240", subject("PROG1", "6", 1), subject("PRACT", "12", 4));
    List<RuleData> rules =
        List.of(
            rule("R-001").subject("PROG1").requires("M01").build(),
            rule("R-004").subject("PRACT").requires("FCT").conditional("Solo si las prácticas son de naturaleza similar").build(),
            rule("R-054").subject("PRACT").requires("FCT").conditional(null).build(),
            rule("R-060").optative("3").conditional("Según valoración").build());

    Recognized result = recognized(title(), TABLE, rules, degree);
    assertThat(result.recognizedEcts()).isEqualByComparingTo("6");
    assertThat(result.conditionalRecognitions()).extracting(ConditionalRecognition::ruleId).containsExactly("R-004", "R-054", "R-060");
    ConditionalRecognition practices = result.conditionalRecognitions().get(0);
    assertThat(practices.subjectId()).isEqualTo("PRACT");
    assertThat(practices.ects()).isEqualByComparingTo("12");
    assertThat(practices.conditionText()).isEqualTo("Solo si las prácticas son de naturaleza similar");
    // Sin condición en el documento: el texto queda vacío para que la interfaz lo indique.
    assertThat(result.conditionalRecognitions().get(1).conditionText()).isNull();
    assertThat(result.conditionalRecognitions().get(2).subjectId()).isNull();
    // La asignatura condicionada sigue entre las restantes.
    assertThat(remainingIds(result)).contains("PRACT");
    // Sin el módulo que exige, la regla condicionada ni siquiera se muestra.
    assertThat(recognized(modules("M01"), TABLE, rules, degree).conditionalRecognitions())
        .extracting(ConditionalRecognition::ruleId)
        .containsExactly("R-060");
  }

  @Test
  @DisplayName("Caso 8: una condicionada no se muestra si su asignatura ya se reconoce sin condiciones")
  void case08ConditionalHiddenWhenSubjectAlreadyRecognized() {
    DegreeData degree = degree("240", subject("PRACT", "12", 4));
    List<RuleData> rules =
        List.of(rule("R-001").subject("PRACT").requires("M01").build(), rule("R-002").subject("PRACT").conditional("Si procede").build());

    Recognized result = recognized(title(), TABLE, rules, degree);
    assertThat(result.recognizedEcts()).isEqualByComparingTo("12");
    assertThat(result.conditionalRecognitions()).isEmpty();
  }

  @Test
  @DisplayName("Caso 9: en un doble grado de más de 240 ECTS el tope se calcula sobre su carga total")
  void case09DoubleDegreeCapUsesTotalLoad() {
    List<SubjectData> subjects = new ArrayList<>();
    List<RuleData> rules = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      subjects.add(subject("S" + pad(i), "6", 1));
      rules.add(rule("R-" + pad(i)).subject("S" + pad(i)).build());
    }
    rules.add(rule("R-099").optative("3").build());
    DegreeData doubleDegree = new DegreeData("UEJ-DG", "Doble Grado de Prueba", new BigDecimal("372"), subjects);
    DegreeData simpleDegree = new DegreeData("UEJ-GP", "Grado de Prueba", new BigDecimal("240"), subjects);

    Recognized inDouble = recognized(title(), TABLE, rules, doubleDegree);
    assertThat(inDouble.legalCapEcts()).isEqualByComparingTo("93");
    assertThat(inDouble.recognizedEcts()).isEqualByComparingTo("75");
    assertThat(inDouble.capped()).isFalse();

    // Las mismas reglas en un grado de 240 ECTS sí se limitarían.
    Recognized inSimple = recognized(title(), TABLE, rules, simpleDegree);
    assertThat(inSimple.recognizedEcts()).isEqualByComparingTo("60");
    assertThat(inSimple.capped()).isTrue();
  }

  @Test
  @DisplayName("Caso 10: el mismo ciclo con dos normativas son ciclos distintos, con módulos distintos")
  void case10SameCycleTwoRegulationsAreDifferentCycles() {
    DegreeData degree = degree("240", subject("PROG1", "6", 1));
    List<RuleData> oldTitleRules = List.of(rule("R-001").subject("PROG1").requires("CF-EJ-M01").build());
    List<RuleData> newTitleRules = List.of(rule("R-029").subject("PROG1").requires("CF-EJ-2024-M01").build());

    // Los módulos del título antiguo no satisfacen las reglas del título actualizado, ni al revés.
    assertThat(recognized(modules("CF-EJ-M01"), TABLE, oldTitleRules, degree).recognizedEcts()).isEqualByComparingTo("6");
    assertThat(recognized(modules("CF-EJ-M01"), TABLE, newTitleRules, degree).recognizedEcts()).isEqualByComparingTo("0");
    assertThat(recognized(modules("CF-EJ-2024-M01"), TABLE, newTitleRules, degree).recognizedEcts()).isEqualByComparingTo("6");
    assertThat(recognized(modules("CF-EJ-2024-M01"), TABLE, oldTitleRules, degree).recognizedEcts()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("Caso 11: cada curso se simula con sus propios datos; el resultado dice de cuál sale")
  void case11EachAcademicYearUsesItsOwnData() {
    List<RuleData> rules = List.of(rule("R-006").subject("WEB").build());
    DegreeData before = degree("240", new SubjectData("WEB", "Programación Web de Prueba", new BigDecimal("6"), "OBLIGATORIA", 2));
    DegreeData after = degree("240", new SubjectData("WEB", "Desarrollo Web de Prueba", new BigDecimal("6"), "OBLIGATORIA", 2));
    SimulationInput input2025 = SimulationInput.withFullTitle("CF-EJ", "UEJ-GP", "2025-26", 1L);
    SimulationInput input2026 = SimulationInput.withFullTitle("CF-EJ", "UEJ-GP", "2026-27", 2L);

    Recognized old = recognized(input2025, TABLE, rules, before);
    Recognized current = recognized(input2026, TABLE, rules, after);

    assertThat(old.recognizedSubjects().get(0).name()).isEqualTo("Programación Web de Prueba");
    assertThat(current.recognizedSubjects().get(0).name()).isEqualTo("Desarrollo Web de Prueba");
    assertThat(old.input().academicYear()).isEqualTo("2025-26");
    assertThat(old.input().datasetVersionId()).isEqualTo(1L);
    assertThat(current.input().datasetVersionId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("Caso 12: el mismo grado en dos centros con tablas distintas da resultados distintos")
  void case12SameDegreeTwoCenters() {
    DegreeData main = degree("240", subject("PROG1", "6", 1), subject("BD", "6", 2));
    DegreeData south = new DegreeData("UEJ-GP-SUR", "Grado de Prueba", new BigDecimal("240"), main.subjects());
    List<RuleData> mainRules = List.of(rule("R-001").subject("PROG1").build(), rule("R-002").subject("BD").requires("M02", "M03").build());
    List<RuleData> southRules = List.of(rule("R-026").subject("BD").requires("M02").build());
    TableData southTable = new TableData("T-EJ-03", TableData.Status.VIGENTE, ADDENDUM, null, null);

    Recognized inMain = recognized(title(), TABLE, mainRules, main);
    Recognized inSouth = recognized(title(), southTable, southRules, south);
    assertThat(inMain.recognizedEcts()).isEqualByComparingTo("12");
    assertThat(inSouth.recognizedEcts()).isEqualByComparingTo("6");
    assertThat(inMain.tableId()).isEqualTo("T-EJ-01");
    assertThat(inSouth.tableId()).isEqualTo("T-EJ-03");
    assertThat(inSouth.recognizedSubjects().get(0).citation().source()).isEqualTo(ADDENDUM);
  }

  @Test
  @DisplayName("Caso 13: ECTS con decimales, sin errores de redondeo")
  void case13DecimalEctsWithoutRoundingErrors() {
    DegreeData degree = degree("250.5", subject("LM", "4.5", 2), subject("IPO", "4.5", 2), subject("X", "0.1", 1), subject("Y", "0.2", 1));
    List<RuleData> rules =
        List.of(
            rule("R-001").subject("LM").build(),
            rule("R-002").subject("IPO").build(),
            rule("R-003").subject("X").build(),
            rule("R-004").subject("Y").build(),
            rule("R-005").optative("4.5").build());

    Recognized result = recognized(title(), TABLE, rules, degree);
    // 4.5 + 4.5 + 0.1 + 0.2 + 4.5 = 13.8 exacto (con double, 0.1 + 0.2 no da 0.3).
    assertThat(result.rulesTotalEcts()).isEqualByComparingTo("13.8");
    assertThat(result.recognizedEcts()).isEqualByComparingTo("13.8");
    // El 25 % de 250.5 es 62.625: no se redondea.
    assertThat(result.legalCapEcts()).isEqualByComparingTo("62.625");
    assertThat(result.pendingEcts()).isEqualByComparingTo("236.7");
  }

  @Test
  @DisplayName("Caso 14: una simulación trabaja sobre una foto fija de los datos y es determinista")
  void case14SimulationUsesAFixedSnapshot() {
    List<SubjectData> subjects = new ArrayList<>(List.of(subject("A", "6", 1), subject("B", "6", 1)));
    List<RuleData> rules = new ArrayList<>(List.of(rule("R-002").subject("B").build(), rule("R-001").subject("A").build()));
    DegreeData degree = new DegreeData("UEJ-GP", "Grado de Prueba", new BigDecimal("240"), subjects);
    SimulationInput input = SimulationInput.withFullTitle("CF-EJ", "UEJ-GP", "2026-27", 7L);

    Recognized first = recognized(input, TABLE, rules, degree);

    // «Se publica otra versión»: las listas originales cambian después de construir los datos.
    subjects.clear();
    Recognized second = recognized(input, TABLE, List.copyOf(rules), degree);
    rules.clear();

    assertThat(second).isEqualTo(first);
    assertThat(first.recognizedSubjects()).hasSize(2);
    assertThat(first.input().datasetVersionId()).isEqualTo(7L);
    // El orden de las reglas de entrada no cambia el resultado.
    List<RuleData> reversed = List.of(rule("R-001").subject("A").build(), rule("R-002").subject("B").build());
    assertThat(recognized(input, TABLE, reversed, degree)).isEqualTo(first);
    // El resultado no se puede modificar.
    assertThatThrownBy(() -> first.recognizedSubjects().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Caso 15: nombres con tildes, comillas y comas llegan intactos al resultado")
  void case15AccentsQuotesAndCommas() {
    String name = "Expresión Oral, Escrita y \"Habilidades Blandas\" de Prueba";
    String excerpt = "Módulo Ficticio 06 \"Demo\", XML y afines";
    DegreeData degree = degree("240", new SubjectData("EXPR", name, new BigDecimal("3"), "OBLIGATORIA", 1));
    List<RuleData> rules = List.of(rule("R-008").subject("EXPR").excerpt(excerpt).build());

    RecognizedSubject subject = recognized(title(), TABLE, rules, degree).recognizedSubjects().get(0);
    assertThat(subject.name()).isEqualTo(name);
    assertThat(subject.citation().excerpt()).isEqualTo(excerpt);
  }

  // ---------- resto del contrato ----------

  @Test
  @DisplayName("Una regla sin módulos (por tener el título) se aplica siempre")
  void ruleWithoutModulesAlwaysApplies() {
    DegreeData degree = degree("240");
    List<RuleData> rules = List.of(rule("R-052").optative("3").build());
    assertThat(recognized(modules(), TABLE, rules, degree).recognizedEcts()).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("Fuente efectiva: la propia de la regla o, si no tiene, la de su tabla")
  void effectiveSourceIsTheRuleSourceOrTheTableSource() {
    DegreeData degree = degree("240", subject("A", "6", 1), subject("B", "6", 1));
    List<RuleData> rules =
        List.of(rule("R-001").subject("A").page(3).build(), rule("R-053").subject("B").source(ADDENDUM).page(2).build());

    Recognized result = recognized(title(), TABLE, rules, degree);
    assertThat(result.recognizedSubjects().get(0).citation().source()).isEqualTo(TABLE_SOURCE);
    assertThat(result.recognizedSubjects().get(0).citation().page()).isEqualTo(3);
    assertThat(result.recognizedSubjects().get(1).citation().source()).isEqualTo(ADDENDUM);
    assertThat(result.recognizedSubjects().get(1).citation().page()).isEqualTo(2);
  }

  @Test
  @DisplayName("recognized_ects de la regla manda sobre los ECTS de la asignatura")
  void ruleEctsOverrideSubjectEcts() {
    DegreeData degree = degree("240", subject("A", "12", 1));
    List<RuleData> rules = List.of(rule("R-001").subject("A").ects("6").build());
    assertThat(recognized(title(), TABLE, rules, degree).recognizedEcts()).isEqualByComparingTo("6");
  }

  @Test
  @DisplayName("Asignaturas restantes agrupadas por curso (desconocido al final) y ECTS pendientes")
  void remainingSubjectsAreGroupedByYear() {
    DegreeData degree =
        degree(
            "240",
            subject("C", "6", 2),
            subject("A", "6", 1),
            subject("B", "6", 1),
            new SubjectData("Z", "Asignatura de Prueba sin curso", null, "OPTATIVA", null),
            subject("D", "6", 2));
    List<RuleData> rules = List.of(rule("R-001").subject("A").build());

    Recognized result = recognized(title(), TABLE, rules, degree);
    assertThat(result.remainingByYear()).extracting(RemainingYear::courseYear).containsExactly(1, 2, null);
    assertThat(remainingIds(result)).containsExactly("B", "C", "D", "Z");
    assertThat(result.degreeTotalEcts()).isEqualByComparingTo("240");
    assertThat(result.pendingEcts()).isEqualByComparingTo("234");
  }

  @Test
  @DisplayName("Las asignaturas reconocidas salen ordenadas por curso y después por identificador")
  void recognizedSubjectsAreSortedByYearThenId() {
    DegreeData degree = degree("240", subject("B", "6", 2), subject("C", "6", 1), subject("A", "6", 2));
    List<RuleData> rules =
        List.of(rule("R-001").subject("B").build(), rule("R-002").subject("C").build(), rule("R-003").subject("A").build());
    assertThat(recognized(title(), TABLE, rules, degree).recognizedSubjects())
        .extracting(RecognizedSubject::subjectId)
        .containsExactly("C", "A", "B");
  }

  @Test
  @DisplayName("Sin reglas aplicables el resultado es 0 ECTS, no un error")
  void noApplicableRulesGivesZero() {
    DegreeData degree = degree("240", subject("A", "6", 1));
    Recognized result = recognized(modules(), TABLE, List.of(rule("R-001").subject("A").requires("M01").build()), degree);
    assertThat(result.recognizedEcts()).isEqualByComparingTo("0");
    assertThat(result.capped()).isFalse();
    assertThat(result.pendingEcts()).isEqualByComparingTo("240");
  }

  @Test
  @DisplayName("El tope legal es un parámetro: con otro valor, el límite cambia")
  void legalCapRatioIsConfigurable() {
    DegreeData degree = degree("240", subject("A", "48", 1));
    List<RuleData> rules = List.of(rule("R-001").subject("A").build());
    Recognized result = (Recognized) new SimulationEngine(new BigDecimal("0.15")).simulate(title(), TABLE, rules, degree);
    assertThat(result.legalCapEcts()).isEqualByComparingTo("36");
    assertThat(result.recognizedEcts()).isEqualByComparingTo("36");
    assertThat(result.capped()).isTrue();

    assertThatThrownBy(() -> new SimulationEngine(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SimulationEngine(new BigDecimal("1.01"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SimulationEngine(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Datos que incumplen el contrato producen un error claro, no un resultado falso")
  void invalidDataIsRejected() {
    DegreeData noTotal = new DegreeData("UEJ-GP", "Grado de Prueba", null, List.of());
    assertThatThrownBy(() -> engine.simulate(title(), TABLE, List.of(), noTotal))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carga total");

    DegreeData degree = degree("240", new SubjectData("SIS", "Sistemas Ficticios", null, "OBLIGATORIA", 2));
    assertThatThrownBy(() -> engine.simulate(title(), TABLE, List.of(rule("R-001").subject("SIS").build()), degree))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no indica ECTS");
    assertThatThrownBy(() -> engine.simulate(title(), TABLE, List.of(rule("R-001").subject("OTRA").build()), degree))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no pertenece al grado");
    assertThatThrownBy(
            () -> engine.simulate(title(), TABLE, List.of(rule("R-001").subject("OTRA").conditional("x").build()), degree))
        .isInstanceOf(IllegalArgumentException.class);
    // Una condicionada sobre una asignatura sin ECTS conocidos se muestra con ECTS vacíos.
    Recognized unknown =
        recognized(title(), TABLE, List.of(rule("R-001").subject("SIS").conditional("x").build()), degree);
    assertThat(unknown.conditionalRecognitions().get(0).ects()).isNull();

    assertThatThrownBy(() -> rule("R-001").build()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new RuleData("R-001", TargetType.OPTATIVE_CREDITS, null, null, false, null, null, 1, null, Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> engine.simulate(null, TABLE, List.of(), degree)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("La entrada distingue «tiene el título» de «tiene estos módulos»")
  void inputDistinguishesFullTitleFromModuleSet() {
    assertThat(title().hasFullTitle()).isTrue();
    assertThat(title().hasAllModules(Set.of("LO-QUE-SEA"))).isTrue();
    SimulationInput partial = modules("M01");
    assertThat(partial.hasFullTitle()).isFalse();
    assertThat(partial.hasAllModules(Set.of())).isTrue();
    assertThat(partial.hasAllModules(Set.of("M01", "M02"))).isFalse();
    assertThatThrownBy(() -> SimulationInput.withModules("CF-EJ", "UEJ-GP", "2026-27", 1L, null))
        .isInstanceOf(NullPointerException.class);
  }

  // ---------- utilidades ----------

  private Recognized recognized(SimulationInput input, TableData table, List<RuleData> rules, DegreeData degree) {
    SimulationResult result = engine.simulate(input, table, rules, degree);
    assertThat(result).isInstanceOf(Recognized.class);
    return (Recognized) result;
  }

  private static SimulationInput title() {
    return SimulationInput.withFullTitle("CF-EJ", "UEJ-GP", "2026-27", 1L);
  }

  private static SimulationInput modules(String... moduleIds) {
    return SimulationInput.withModules("CF-EJ", "UEJ-GP", "2026-27", 1L, Set.of(moduleIds));
  }

  private static DegreeData degree(String totalEcts, SubjectData... subjects) {
    return new DegreeData("UEJ-GP", "Grado de Prueba", new BigDecimal(totalEcts), List.of(subjects));
  }

  private static SubjectData subject(String id, String ects, Integer year) {
    return new SubjectData(id, "Asignatura de Prueba " + id, new BigDecimal(ects), "OBLIGATORIA", year);
  }

  private static List<String> remainingIds(Recognized result) {
    return result.remainingByYear().stream().flatMap(y -> y.subjects().stream()).map(SubjectData::id).toList();
  }

  private static String pad(int i) {
    return String.format("%03d", i);
  }

  private static RuleBuilder rule(String id) {
    return new RuleBuilder(id);
  }

  /** Construye reglas de prueba con pocos datos. */
  private static final class RuleBuilder {
    private final String id;
    private TargetType type;
    private String subjectId;
    private BigDecimal ects;
    private boolean conditional;
    private String conditionText;
    private SourceRef source;
    private int page = 1;
    private String excerpt = "Fragmento ficticio";
    private Set<String> modules = Set.of();

    private RuleBuilder(String id) {
      this.id = id;
    }

    RuleBuilder subject(String subjectId) {
      this.type = TargetType.SUBJECT;
      this.subjectId = subjectId;
      return this;
    }

    RuleBuilder optative(String ects) {
      this.type = TargetType.OPTATIVE_CREDITS;
      this.ects = new BigDecimal(ects);
      return this;
    }

    RuleBuilder ects(String ects) {
      this.ects = new BigDecimal(ects);
      return this;
    }

    RuleBuilder requires(String... moduleIds) {
      this.modules = Set.of(moduleIds);
      return this;
    }

    RuleBuilder conditional(String conditionText) {
      this.conditional = true;
      this.conditionText = conditionText;
      return this;
    }

    RuleBuilder source(SourceRef source) {
      this.source = source;
      return this;
    }

    RuleBuilder page(int page) {
      this.page = page;
      return this;
    }

    RuleBuilder excerpt(String excerpt) {
      this.excerpt = excerpt;
      return this;
    }

    RuleData build() {
      if (type == null) {
        throw new IllegalArgumentException("La regla de prueba " + id + " no tiene destino");
      }
      return new RuleData(id, type, subjectId, ects, conditional, conditionText, source, page, excerpt, modules);
    }
  }
}
