package es.convalidafp.contract;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.contract.Dataset.Row;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Criterio de cierre de la Fase 0: el dataset de ejemplo cumple el contrato y representa los 15
 * casos límite de la especificación.
 */
class SampleDatasetContractTest {

  private static final Path SAMPLE =
      Path.of(System.getProperty("convalidafp.dataDir", "../data")).resolve("sample");
  private static final Pattern FICTITIOUS =
      Pattern.compile("Ejemplo|Prueba|Fictici|Demostra", Pattern.CASE_INSENSITIVE);
  private static final BigDecimal QUARTER = new BigDecimal("0.25");

  private static Dataset current;
  private static Dataset previous;

  @BeforeAll
  static void load() {
    current = Dataset.load(SAMPLE.resolve("2026-27"));
    previous = Dataset.load(SAMPLE.resolve("2025-26"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025-26", "2026-27"})
  @DisplayName("El dataset de ejemplo cumple el contrato")
  void sampleDatasetCompliesWithContract(String year) {
    List<String> errors = ContractChecker.check(Dataset.load(SAMPLE.resolve(year))).errors();
    assertThat(errors).as(ContractChecker.describe(errors)).isEmpty();
  }

  @Test
  @DisplayName("Los avisos no bloquean: regla sin módulos y regla condicionada sin condición")
  void sampleDatasetProducesExactlyTheExpectedWarnings() {
    assertThat(ContractChecker.check(previous).warnings()).isEmpty();
    List<String> warnings = ContractChecker.check(current).warnings();
    assertThat(warnings).hasSize(2);
    assertThat(warnings).anyMatch(w -> w.startsWith("recognition_rules.csv: fila 53: columna rule_id"));
    assertThat(warnings).anyMatch(w -> w.startsWith("recognition_rules.csv: fila 55: columna condition_text"));
  }

  @Test
  @DisplayName("Una regla puede citar una fuente propia distinta de la de su tabla")
  void ruleCanCiteItsOwnSource() {
    Row rule = row(current, "recognition_rules.csv", "rule_id", "R-053");
    assertThat(rule.get("source_id")).isEqualTo("S-EJ-04").isNotEqualTo(table("T-EJ-01").get("source_id"));
    assertThat(rules(r -> r.blank("source_id"))).isNotEmpty();
  }

  @Test
  @DisplayName("Regla 2: todos los nombres del dataset de ejemplo son inequívocamente ficticios")
  void sampleNamesAreUnmistakablyFictitious() {
    for (Dataset dataset : List.of(current, previous)) {
      Stream.of("universities.csv", "fp_cycles.csv", "fp_modules.csv", "degrees.csv", "degree_subjects.csv")
          .flatMap(file -> dataset.rows(file).stream())
          .forEach(r -> assertThat(r.get("name")).as(r.file() + " fila " + r.line()).containsPattern(FICTITIOUS));
      dataset
          .rows("source_documents.csv")
          .forEach(r -> assertThat(r.get("title")).as("fuente " + r.get("source_id")).containsPattern(FICTITIOUS));
    }
  }

  // ---------- El comprobador detecta datasets rotos (no es un test vacío) ----------

  @Test
  void checkerRejectsZeroEcts(@TempDir Path tmp) throws IOException {
    Path dir = copyOf("2025-26", tmp);
    replaceInFile(dir.resolve("fp_modules.csv"), ",14,1", ",0,1");
    assertThat(ContractChecker.check(Dataset.load(dir)).errors())
        .anyMatch(e -> e.startsWith("fp_modules.csv: fila 2: columna ects"))
        .anyMatch(e -> e.contains("checksum de fp_modules.csv"));
  }

  @Test
  void checkerRejectsBrokenForeignKey(@TempDir Path tmp) throws IOException {
    Path dir = copyOf("2025-26", tmp);
    replaceInFile(dir.resolve("rule_requirements.csv"), "R-001,CF-EJ-M01", "R-001,CF-EJ-M99");
    assertThat(ContractChecker.check(Dataset.load(dir)).errors())
        .anyMatch(e -> e.startsWith("rule_requirements.csv: fila 2: columna module_id"));
  }

  @Test
  void checkerRejectsDegreeWithActiveTableAndUnknownTotalEcts(@TempDir Path tmp) throws IOException {
    Path dir = copyOf("2025-26", tmp);
    replaceInFile(dir.resolve("degrees.csv"), ",240,PLAN-EJ-2020", ",,PLAN-EJ-2020");
    assertThat(ContractChecker.check(Dataset.load(dir)).errors())
        .anyMatch(e -> e.startsWith("degrees.csv: fila 2: columna total_ects"));
  }

  @Test
  void checkerRejectsTargetSubjectWithUnknownEcts(@TempDir Path tmp) throws IOException {
    Path dir = copyOf("2025-26", tmp);
    replaceInFile(dir.resolve("degree_subjects.csv"), ",6,BASICA,1,EJ1001", ",,BASICA,1,EJ1001");
    assertThat(ContractChecker.check(Dataset.load(dir)).errors())
        .anyMatch(e -> e.startsWith("degree_subjects.csv: fila 2: columna ects"));
  }

  // ---------- Los 15 casos límite están representados ----------

  @Test
  @DisplayName("Caso 1: NO_DATA (sin fila) frente a SIN_CONVENIO verificado")
  void case01NoDataVersusNoAgreement() {
    assertThat(tables(r -> r.get("status").equals("SIN_CONVENIO"))).isNotEmpty();
    Set<String> withRow =
        current.rows("recognition_tables.csv").stream()
            .map(r -> r.get("cycle_id") + "|" + r.get("degree_id"))
            .collect(Collectors.toSet());
    long allPairs = (long) current.rows("fp_cycles.csv").size() * current.rows("degrees.csv").size();
    assertThat(allPairs).isGreaterThan(withRow.size());
  }

  @Test
  @DisplayName("Caso 2: asignatura que exige dos módulos (Y)")
  void case02RuleRequiringTwoModules() {
    assertThat(modulesByRule().get("R-002")).containsExactlyInAnyOrder("CF-EJ-M02", "CF-EJ-M03");
  }

  @Test
  @DisplayName("Caso 3: misma asignatura alcanzable por dos reglas (O)")
  void case03SameSubjectReachableByTwoRules() {
    assertThat(rules(r -> r.get("table_id").equals("T-EJ-01") && r.get("target_subject_id").equals("UEJ-GP-WEB")))
        .extracting(r -> r.get("rule_id"))
        .containsExactly("R-006", "R-007");
  }

  @Test
  @DisplayName("Caso 4: un módulo que sirve para varias asignaturas")
  void case04OneModuleServesSeveralSubjects() {
    Map<String, Set<String>> modules = modulesByRule();
    assertThat(rules(r -> r.get("table_id").equals("T-EJ-01") && Set.of("CF-EJ-M01").equals(modules.get(r.get("rule_id")))))
        .extracting(r -> r.get("target_subject_id"))
        .containsExactlyInAnyOrder("UEJ-GP-PROG1", "UEJ-GP-PROG2");
  }

  @Test
  @DisplayName("Caso 5: la suma de las reglas supera el 25 % del grado")
  void case05SumExceedsLegalCap() {
    assertThat(ruleSum("T-PR-01")).isEqualByComparingTo("66");
    assertThat(legalCap("T-PR-01")).isEqualByComparingTo("60");
  }

  @Test
  @DisplayName("Caso 6: tope de tabla inferior al 25 %")
  void case06TableCapBelowLegalCap() {
    Row table = table("T-PR-02");
    assertThat(new BigDecimal(table.get("table_max_ects"))).isLessThan(legalCap("T-PR-02"));
    assertThat(ruleSum("T-PR-02")).isGreaterThan(new BigDecimal(table.get("table_max_ects")));
  }

  @Test
  @DisplayName("Caso 7: créditos optativos genéricos")
  void case07GenericOptativeCredits() {
    assertThat(rules(r -> r.get("target_type").equals("OPTATIVE_CREDITS") && r.blank("target_subject_id") && !r.blank("recognized_ects")))
        .isNotEmpty();
  }

  @Test
  @DisplayName("Caso 8: regla condicionada (prácticas externas)")
  void case08ConditionalRule() {
    assertThat(rules(r -> r.get("is_conditional").equals("true") && !r.blank("condition_text")))
        .extracting(r -> r.get("rule_id"))
        .contains("R-004");
  }

  @Test
  @DisplayName("Caso 9: doble grado con más de 240 ECTS; el tope se calcula sobre su carga total")
  void case09DoubleDegree() {
    Row degree = row(current, "degrees.csv", "degree_id", "UEJ-DG");
    assertThat(degree.get("degree_type")).isEqualTo("DOBLE");
    assertThat(new BigDecimal(degree.get("total_ects"))).isGreaterThan(new BigDecimal("240"));
    // 75 ECTS: por encima del 25 % de 240 (60) y por debajo del 25 % de 372 (93).
    assertThat(ruleSum("T-EJ-02")).isGreaterThan(new BigDecimal("60")).isLessThan(legalCap("T-EJ-02"));
  }

  @Test
  @DisplayName("Caso 10: mismo ciclo con dos normativas = dos ciclos")
  void case10SameCycleTwoRegulations() {
    Row old = row(current, "fp_cycles.csv", "cycle_id", "CF-EJ");
    Row updated = row(current, "fp_cycles.csv", "cycle_id", "CF-EJ-2024");
    assertThat(old.get("name")).isEqualTo(updated.get("name"));
    assertThat(old.get("regulation_ref")).isNotEqualTo(updated.get("regulation_ref"));
    assertThat(tables(r -> r.get("cycle_id").equals("CF-EJ-2024"))).isNotEmpty();
  }

  @Test
  @DisplayName("Caso 11: asignatura renombrada entre cursos, versionada sin sobrescribir")
  void case11SubjectRenamedBetweenYears() {
    String before = row(previous, "degree_subjects.csv", "subject_id", "UEJ-GP-WEB").get("name");
    String after = row(current, "degree_subjects.csv", "subject_id", "UEJ-GP-WEB").get("name");
    assertThat(before).isNotEqualTo(after);
  }

  @Test
  @DisplayName("Caso 12: mismo grado en dos centros con tablas distintas")
  void case12SameDegreeTwoCenters() {
    Row main = row(current, "degrees.csv", "degree_id", "UEJ-GP");
    Row south = row(current, "degrees.csv", "degree_id", "UEJ-GP-SUR");
    assertThat(main.get("name")).isEqualTo(south.get("name"));
    assertThat(main.get("university_id")).isEqualTo(south.get("university_id"));
    assertThat(main.get("center")).isNotEqualTo(south.get("center"));
    assertThat(ruleSum("T-EJ-01")).isNotEqualByComparingTo(ruleSum("T-EJ-03"));
  }

  @Test
  @DisplayName("Caso 13: ECTS con decimales")
  void case13DecimalEcts() {
    assertThat(ruleSum("T-EJ-01")).isEqualByComparingTo("51");
    assertThat(ruleSum("T-EJ-03")).isEqualByComparingTo("22.5");
    assertThat(row(current, "degree_subjects.csv", "subject_id", "UEJ-GP-LM").get("ects")).isEqualTo("4.5");
  }

  @Test
  @DisplayName("Caso 14: hay dos versiones publicables para probar la publicación concurrente")
  void case14TwoPublishableVersions() {
    assertThat(previous.rows("recognition_tables.csv")).isNotEmpty();
    assertThat(current.rows("recognition_tables.csv")).isNotEmpty();
  }

  @Test
  @DisplayName("Caso 15: nombres con tildes, comillas y comas")
  void case15AccentsQuotesAndCommas() {
    Predicate<String> tricky = s -> s.contains("\"") && s.contains(",") && s.matches(".*[áéíóúñ].*");
    assertThat(current.rows("universities.csv")).anyMatch(r -> tricky.test(r.get("name")));
    assertThat(current.rows("fp_modules.csv")).anyMatch(r -> tricky.test(r.get("name")));
    assertThat(current.rows("degree_subjects.csv")).anyMatch(r -> tricky.test(r.get("name")));
  }

  // ---------- utilidades ----------

  private static List<Row> tables(Predicate<Row> filter) {
    return current.rows("recognition_tables.csv").stream().filter(filter).toList();
  }

  private static List<Row> rules(Predicate<Row> filter) {
    return current.rows("recognition_rules.csv").stream().filter(filter).toList();
  }

  private static Row table(String tableId) {
    return row(current, "recognition_tables.csv", "table_id", tableId);
  }

  private static Row row(Dataset dataset, String file, String column, String value) {
    return dataset.rows(file).stream().filter(r -> r.get(column).equals(value)).findFirst().orElseThrow();
  }

  private static Map<String, Set<String>> modulesByRule() {
    Map<String, Set<String>> result = new HashMap<>();
    for (Row r : current.rows("rule_requirements.csv")) {
      result.computeIfAbsent(r.get("rule_id"), k -> new HashSet<>()).add(r.get("module_id"));
    }
    return result;
  }

  /** Suma sin tope de una tabla: asignaturas distintas no condicionadas más créditos optativos. */
  private static BigDecimal ruleSum(String tableId) {
    BigDecimal sum = BigDecimal.ZERO;
    Set<String> counted = new HashSet<>();
    for (Row r : rules(x -> x.get("table_id").equals(tableId) && x.get("is_conditional").equals("false"))) {
      if (r.get("target_type").equals("OPTATIVE_CREDITS")) {
        sum = sum.add(new BigDecimal(r.get("recognized_ects")));
      } else if (counted.add(r.get("target_subject_id"))) {
        String own = r.get("recognized_ects");
        String ects = own.isEmpty() ? row(current, "degree_subjects.csv", "subject_id", r.get("target_subject_id")).get("ects") : own;
        sum = sum.add(new BigDecimal(ects));
      }
    }
    return sum;
  }

  private static BigDecimal legalCap(String tableId) {
    Row degree = row(current, "degrees.csv", "degree_id", table(tableId).get("degree_id"));
    return new BigDecimal(degree.get("total_ects")).multiply(QUARTER);
  }

  private static Path copyOf(String year, Path tmp) throws IOException {
    Path target = Files.createDirectory(tmp.resolve(year));
    try (Stream<Path> files = Files.list(SAMPLE.resolve(year))) {
      for (Path file : files.toList()) {
        Files.copy(file, target.resolve(file.getFileName()));
      }
    }
    return target;
  }

  private static void replaceInFile(Path file, String search, String replacement) throws IOException {
    String content = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(content).contains(search);
    Files.writeString(file, content.replaceFirst(Pattern.quote(search), replacement), StandardCharsets.UTF_8);
  }
}
