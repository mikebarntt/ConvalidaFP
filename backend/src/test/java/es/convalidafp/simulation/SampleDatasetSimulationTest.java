package es.convalidafp.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.contract.Dataset;
import es.convalidafp.simulation.DegreeData.SubjectData;
import es.convalidafp.simulation.SimulationResult.ConditionalRecognition;
import es.convalidafp.simulation.SimulationResult.NoAgreement;
import es.convalidafp.simulation.SimulationResult.NoData;
import es.convalidafp.simulation.SimulationResult.OptativeCredits;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.simulation.SimulationResult.RecognizedSubject;
import es.convalidafp.testsupport.CsvSimulation;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * El motor contra el dataset de ejemplo completo, leído directamente de los CSV (sin base de
 * datos). Los valores esperados son los de la tabla de data/sample/README.md.
 */
class SampleDatasetSimulationTest {

  private static final Path SAMPLE = Path.of(System.getProperty("convalidafp.dataDir", "../data")).resolve("sample");

  private final SimulationEngine engine = new SimulationEngine(new BigDecimal("0.25"));
  private final Dataset current = Dataset.load(SAMPLE.resolve("2026-27"));
  private final Dataset previous = Dataset.load(SAMPLE.resolve("2025-26"));

  @ParameterizedTest(name = "{0} × {1}: suma {2}, tope {3}, reconocido {4}, limitado {5}")
  @CsvSource({
    "CF-EJ,      UEJ-GP,     51,   60, 51,   false",
    "CF-EJ,      UEJ-DG,     75,   93, 75,   false",
    "CF-EJ,      UEJ-GP-SUR, 22.5, 60, 22.5, false",
    "CF-EJ-2024, UEJ-GP,     39,   60, 39,   false",
    "CF-EJ,      UPR-GD,     66,   60, 60,   true",
    "CF-PR,      UPR-GD,     36,   30, 30,   true",
  })
  @DisplayName("Resultados esperados del curso 2026-27")
  void expectedResultsOfTheSampleDataset(
      String cycleId, String degreeId, String sum, String cap, String recognized, boolean capped) {
    Recognized result = (Recognized) simulate(current, "2026-27", cycleId, degreeId);

    assertThat(result.rulesTotalEcts()).isEqualByComparingTo(sum);
    assertThat(result.appliedCapEcts()).isEqualByComparingTo(cap);
    assertThat(result.recognizedEcts()).isEqualByComparingTo(recognized);
    assertThat(result.capped()).isEqualTo(capped);
    // La suma es coherente con el desglose, y todo reconocimiento lleva su fuente y su página.
    BigDecimal breakdown =
        result.recognizedSubjects().stream().map(RecognizedSubject::ects).reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(result.optativeCredits().stream().map(OptativeCredits::ects).reduce(BigDecimal.ZERO, BigDecimal::add));
    assertThat(breakdown).isEqualByComparingTo(sum);
    assertThat(result.recognizedSubjects()).allSatisfy(s -> {
      assertThat(s.citation().source()).isNotNull();
      assertThat(s.citation().page()).isPositive();
    });
    assertThat(result.pendingEcts()).isEqualByComparingTo(result.degreeTotalEcts().subtract(result.recognizedEcts()));
  }

  @Test
  @DisplayName("Caso 1: SIN_CONVENIO da NO_AGREEMENT y una combinación sin fila da NO_DATA")
  void noAgreementAndNoData() {
    SimulationResult withSource = simulate(current, "2026-27", "CF-EJ", "UFI-GS");
    assertThat(withSource).isInstanceOf(NoAgreement.class);
    assertThat(((NoAgreement) withSource).source().id()).isEqualTo("S-FI-01");

    SimulationResult withoutSource = simulate(current, "2026-27", "CF-PR", "UEJ-DG");
    assertThat(withoutSource).isInstanceOf(NoAgreement.class);
    assertThat(((NoAgreement) withoutSource).source()).isNull();

    assertThat(simulate(current, "2026-27", "CF-PR", "UEJ-GP")).isInstanceOf(NoData.class);
    assertThat(simulate(current, "2026-27", "CF-EJ-2024", "UPR-GD")).isInstanceOf(NoData.class);
  }

  @Test
  @DisplayName("CF-EJ × UEJ-GP al detalle: casos 2, 3, 4, 7, 8 y 13, y fuente por regla")
  void mainTableInDetail() {
    Recognized result = (Recognized) simulate(current, "2026-27", "CF-EJ", "UEJ-GP");

    assertThat(result.tableId()).isEqualTo("T-EJ-01");
    assertThat(result.recognizedSubjects())
        .extracting(RecognizedSubject::subjectId)
        .containsExactly(
            "UEJ-GP-EMP", "UEJ-GP-PROG1", "UEJ-GP-PROG2", "UEJ-GP-BD", "UEJ-GP-IPO", "UEJ-GP-IS1", "UEJ-GP-LM", "UEJ-GP-WEB");

    RecognizedSubject web = subject(result, "UEJ-GP-WEB");
    assertThat(web.ruleId()).isEqualTo("R-006");
    assertThat(web.alternativeRuleIds()).containsExactly("R-007");
    assertThat(web.citation().source().id()).isEqualTo("S-EJ-01");

    assertThat(subject(result, "UEJ-GP-LM").ects()).isEqualByComparingTo("4.5");
    assertThat(subject(result, "UEJ-GP-IS1").citation().source().id()).isEqualTo("S-EJ-04");
    assertThat(subject(result, "UEJ-GP-IS1").citation().page()).isEqualTo(2);

    assertThat(result.optativeCredits()).extracting(OptativeCredits::ruleId).containsExactly("R-003");
    assertThat(result.conditionalRecognitions()).extracting(ConditionalRecognition::ruleId).containsExactly("R-004");
    assertThat(result.conditionalRecognitions().get(0).ects()).isEqualByComparingTo("12");

    // Quedan 32 de las 40 asignaturas, en los 4 cursos; faltan 189 ECTS de 240.
    assertThat(result.remainingByYear()).hasSize(4);
    assertThat(result.remainingByYear().stream().mapToInt(y -> y.subjects().size()).sum()).isEqualTo(32);
    assertThat(result.pendingEcts()).isEqualByComparingTo("189");
  }

  @Test
  @DisplayName("Caso 2 con el dataset: sin uno de los dos módulos, Bases de Datos no se reconoce")
  void andRuleWithPartialModules() {
    Set<String> withoutM03 = modulesOf(current, "CF-EJ");
    withoutM03.remove("CF-EJ-M03");

    Recognized result = (Recognized) simulate(current, "2026-27", "CF-EJ", "UEJ-GP", withoutM03);
    assertThat(result.recognizedSubjects()).extracting(RecognizedSubject::subjectId).doesNotContain("UEJ-GP-BD");
    assertThat(result.recognizedEcts()).isEqualByComparingTo("45");
  }

  @Test
  @DisplayName("Caso 5 con el dataset: 66 ECTS limitados a 60 sin descartar ninguna de las 9 asignaturas")
  void cappedTableKeepsEverySubject() {
    Recognized result = (Recognized) simulate(current, "2026-27", "CF-EJ", "UPR-GD");
    assertThat(result.recognizedSubjects()).hasSize(9);
    assertThat(result.optativeCredits()).hasSize(1);
    assertThat(result.conditionalRecognitions()).extracting(ConditionalRecognition::ruleId).containsExactly("R-045");
  }

  @Test
  @DisplayName("Casos 8 y regla sin módulos con el dataset: condición sin especificar y créditos por título")
  void conditionWithoutTextAndRuleWithoutModules() {
    Recognized south = (Recognized) simulate(current, "2026-27", "CF-EJ", "UEJ-GP-SUR");
    assertThat(south.conditionalRecognitions()).hasSize(1);
    assertThat(south.conditionalRecognitions().get(0).conditionText()).isNull();

    // R-052 no exige módulos: se aplica aunque el estudiante no tenga ninguno superado.
    Recognized noModules = (Recognized) simulate(current, "2026-27", "CF-EJ-2024", "UEJ-GP", Set.of());
    assertThat(noModules.optativeCredits()).extracting(OptativeCredits::ruleId).containsExactly("R-052");
    assertThat(noModules.recognizedEcts()).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("Caso 10 con el dataset: los módulos del título antiguo no valen para la tabla del actualizado")
  void oldTitleModulesDoNotSatisfyUpdatedTitleRules() {
    Recognized result = (Recognized) simulate(current, "2026-27", "CF-EJ-2024", "UEJ-GP", modulesOf(current, "CF-EJ"));
    assertThat(result.recognizedSubjects()).isEmpty();
    assertThat(result.recognizedEcts()).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("Caso 11 con el dataset: 2025-26 y 2026-27 se simulan por separado")
  void eachYearIsSimulatedWithItsOwnData() {
    Recognized old = (Recognized) simulate(previous, "2025-26", "CF-EJ", "UEJ-GP");
    Recognized now = (Recognized) simulate(current, "2026-27", "CF-EJ", "UEJ-GP");

    assertThat(old.recognizedEcts()).isEqualByComparingTo("30");
    assertThat(subject(old, "UEJ-GP-WEB").name()).isEqualTo("Programación Web de Prueba");
    assertThat(subject(now, "UEJ-GP-WEB").name()).isEqualTo("Desarrollo Web de Prueba");
    assertThat(old.input().academicYear()).isEqualTo("2025-26");
  }

  @Test
  @DisplayName("Caso 14 con el dataset: la misma entrada da siempre el mismo resultado")
  void sameInputSameResult() {
    assertThat(simulate(current, "2026-27", "CF-EJ", "UPR-GD")).isEqualTo(simulate(current, "2026-27", "CF-EJ", "UPR-GD"));
  }

  @Test
  @DisplayName("Caso 15 con el dataset: tildes, comillas y comas llegan al resultado")
  void accentsQuotesAndCommasReachTheResult() {
    Recognized result = (Recognized) simulate(current, "2026-27", "CF-EJ", "UEJ-GP");
    assertThat(subject(result, "UEJ-GP-LM").citation().excerpt())
        .isEqualTo("Módulo Ficticio 06 \"Demo\", XML y afines - Lenguajes de Marcas (4,5 ECTS)");
    List<String> remainingNames =
        result.remainingByYear().stream().flatMap(y -> y.subjects().stream()).map(SubjectData::name).toList();
    assertThat(remainingNames).contains("Expresión Oral, Escrita y \"Habilidades Blandas\" de Prueba");
  }

  // ---------- utilidades ----------

  private SimulationResult simulate(Dataset data, String year, String cycleId, String degreeId) {
    return simulate(data, year, cycleId, degreeId, null);
  }

  private SimulationResult simulate(Dataset data, String year, String cycleId, String degreeId, Set<String> modules) {
    return CsvSimulation.simulate(engine, data, year, cycleId, degreeId, modules);
  }

  private static Set<String> modulesOf(Dataset data, String cycleId) {
    return data.rows("fp_modules.csv").stream()
        .filter(r -> r.get("cycle_id").equals(cycleId))
        .map(r -> r.get("module_id"))
        .collect(Collectors.toCollection(HashSet::new));
  }

  private static RecognizedSubject subject(Recognized result, String subjectId) {
    return result.recognizedSubjects().stream().filter(s -> s.subjectId().equals(subjectId)).findFirst().orElseThrow();
  }
}
