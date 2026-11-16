package es.convalidafp.ingestion;

import static es.convalidafp.ingestion.DatasetContract.CYCLES;
import static es.convalidafp.ingestion.DatasetContract.DEGREES;
import static es.convalidafp.ingestion.DatasetContract.MANIFEST;
import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.REQUIREMENTS;
import static es.convalidafp.ingestion.DatasetContract.RULES;
import static es.convalidafp.ingestion.DatasetContract.SOURCES;
import static es.convalidafp.ingestion.DatasetContract.SUBJECTS;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static es.convalidafp.ingestion.DatasetContract.UNIVERSITIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.convalidafp.ingestion.ValidationReport.Issue;
import es.convalidafp.ingestion.ValidationReport.Severity;
import es.convalidafp.testsupport.SampleFiles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Criterio de cierre de la Fase 3: cada regla de validación de CONTRACT.md tiene un test con un CSV
 * que la rompe. Cada test parte del dataset de ejemplo, cambia una sola cosa y comprueba el código,
 * el fichero, la fila y la columna de la incidencia.
 */
class DatasetValidatorTest {

  private static final String RULE_1_TAIL = ",,3,Módulo Ficticio 01 - Programación I";

  private final DatasetValidator validator = new DatasetValidator();

  // ---------- datasets correctos ----------

  @ParameterizedTest
  @ValueSource(strings = {"2025-26", "2026-27"})
  @DisplayName("El dataset de ejemplo no tiene errores")
  void sampleDatasetIsValid(String year) {
    ValidationReport report = validate(SampleFiles.of(year));
    assertThat(report.errors()).isEmpty();
    assertThat(report.isValid()).isTrue();
  }

  /** help_text de CF-EJ en el dataset de ejemplo, con sus comillas. */
  private static final String HELP_TEXT =
      "\"Si tu título cita la Norma Ficticia 01/2010 (planes anteriores a 2024), este es tu ciclo.\"";

  @Test
  @DisplayName("Se lee el manifiesto: curso, fecha y revisor")
  void manifestIsRead() {
    RawDataset dataset = validator.validate(SampleFiles.of("2025-26").build()).dataset();
    assertThat(dataset.academicYear()).isEqualTo("2025-26");
    assertThat(dataset.manifestDate()).hasToString("2025-10-01");
    assertThat(dataset.reviewer()).isEqualTo("Revisor Ficticio");
    assertThat(dataset.rows(RULES)).hasSize(6);
    assertThat(dataset.rows("otro.csv")).isEmpty();
  }

  @Test
  @DisplayName("Un informe reúne todos los errores, no solo el primero")
  void reportCollectsEveryError() {
    ValidationReport report =
        validate(
            small()
                .replace(MODULES, ",14,1", ",0,1")
                .replace(UNIVERSITIES, ",PUBLICA,", ",ESTATAL,")
                .replace(DEGREES, ",240,PLAN-EJ-2020", ",,PLAN-EJ-2020"));
    assertThat(report.isValid()).isFalse();
    assertThat(report.errors()).extracting(Issue::code).contains("V3", "F5", "V11");
    assertThat(report.errors()).allMatch(i -> i.severity() == Severity.ERROR);
    assertThat(report.errors().get(0).toString()).startsWith("[").contains("fila 2").contains("columna");
  }

  // ---------- V1 a V12: validaciones de la especificación ----------

  @Test
  @DisplayName("V1: clave foránea que no existe")
  void v1BrokenForeignKey() {
    ValidationReport report = validate(small().replace(REQUIREMENTS, "R-001,CF-EJ-M01", "R-001,CF-EJ-M99"));
    assertError(report, "V1", REQUIREMENTS, 2, "module_id");
  }

  @Test
  @DisplayName("V2: clave duplicada")
  void v2DuplicateKey() {
    assertError(
        validate(small().append(UNIVERSITIES, "UEJ,Universidad de Ejemplo Repetida,UEJ,PUBLICA,,")),
        "V2",
        UNIVERSITIES,
        3,
        "university_id");
    assertError(validate(small().append(REQUIREMENTS, "R-001,CF-EJ-M01")), "V2", REQUIREMENTS, 9, "module_id");
  }

  @Test
  @DisplayName("V3: ECTS que no son mayores que 0")
  void v3EctsMustBePositive() {
    assertError(validate(small().replace(MODULES, ",14,1", ",0,1")), "V3", MODULES, 2, "ects");
    assertError(validate(small().replace(MODULES, ",14,1", ",0.00,1")), "V3", MODULES, 2, "ects");
  }

  @Test
  @DisplayName("V4: la asignatura destino no pertenece al grado de la tabla")
  void v4TargetSubjectOfAnotherDegree() {
    ValidationReport report =
        validate(full().replace(RULES, "R-001,T-EJ-01,SUBJECT,UEJ-GP-PROG1", "R-001,T-EJ-01,SUBJECT,UPR-GD-PROG"));
    assertError(report, "V4", RULES, 2, "target_subject_id");
  }

  @Test
  @DisplayName("V5: el módulo exigido no pertenece al ciclo de la tabla")
  void v5ModuleOfAnotherCycle() {
    ValidationReport report = validate(full().replace(REQUIREMENTS, "R-001,CF-EJ-M01", "R-001,CF-PR-M01"));
    assertError(report, "V5", REQUIREMENTS, 2, "module_id");
  }

  @Test
  @DisplayName("V6: tabla VIGENTE sin documento fuente")
  void v6ActiveTableWithoutSource() {
    assertError(validate(small().replace(TABLES, "VIGENTE,S-EJ-01", "VIGENTE,")), "V6", TABLES, 2, "source_id");
  }

  @Test
  @DisplayName("V7: tabla VIGENTE sin ninguna regla")
  void v7ActiveTableWithoutRules() {
    ValidationReport report = validate(small().keepOnlyHeader(RULES).keepOnlyHeader(REQUIREMENTS));
    assertError(report, "V7", TABLES, 2, "table_id");
  }

  @Test
  @DisplayName("V8: regla sin página de la fuente")
  void v8RuleWithoutSourcePage() {
    ValidationReport report = validate(small().replace(RULES, RULE_1_TAIL, ",,,Módulo Ficticio 01 - Programación I"));
    assertError(report, "V8", RULES, 2, "source_page");
  }

  @Test
  @DisplayName("V9: dos tablas para el mismo ciclo, grado y curso")
  void v9DuplicateCombination() {
    ValidationReport report = validate(small().append(TABLES, "T-EJ-99,CF-EJ,UEJ-GP,2025-26,SIN_CONVENIO,,,"));
    assertError(report, "V9", TABLES, 3, "table_id");
  }

  @Test
  @DisplayName("V10: regla duplicada (mismo destino y mismos módulos)")
  void v10DuplicateRule() {
    ValidationReport report =
        validate(
            small()
                .append(RULES, "R-099,T-EJ-01,SUBJECT,UEJ-GP-PROG1,,false,,,3,Duplicada de prueba")
                .append(REQUIREMENTS, "R-099,CF-EJ-M01"));
    assertError(report, "V10", RULES, 8, "rule_id");
  }

  @Test
  @DisplayName("V11: grado con tabla VIGENTE sin carga total")
  void v11DegreeWithoutTotalEcts() {
    assertError(validate(small().replace(DEGREES, ",240,PLAN-EJ-2020", ",,PLAN-EJ-2020")), "V11", DEGREES, 2, "total_ects");
  }

  @Test
  @DisplayName("V12: asignatura destino sin ECTS y regla sin recognized_ects")
  void v12TargetSubjectWithoutEcts() {
    assertError(validate(small().replace(SUBJECTS, ",6,BASICA,1,EJ1001", ",,BASICA,1,EJ1001")), "V12", SUBJECTS, 2, "ects");
    // Si la regla indica sus propios ECTS, la asignatura puede no tenerlos.
    ValidationReport withRuleEcts =
        validate(
            small()
                .replace(SUBJECTS, ",6,BASICA,1,EJ1001", ",,BASICA,1,EJ1001")
                .replace(RULES, "UEJ-GP-PROG1,,false", "UEJ-GP-PROG1,6,false"));
    assertThat(withRuleEcts.errors()).isEmpty();
  }

  // ---------- F1 a F11: validaciones de formato ----------

  @Test
  @DisplayName("F1: falta un fichero, falta el manifiesto, BOM o UTF-8 no válido")
  void f1MissingOrUnreadableFiles() {
    assertFileError(validate(small().remove(CYCLES)), "F1", CYCLES);
    assertFileError(validate(small().remove(MANIFEST)), "F1", MANIFEST);
    assertFileError(validate(small().set(MANIFEST, "esto no es JSON")), "F1", MANIFEST);

    byte[] original = small().text(UNIVERSITIES).getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[original.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(original, 0, withBom, 3, original.length);
    assertFileError(validate(small().setBytes(UNIVERSITIES, withBom)), "F1", UNIVERSITIES);

    // «Universidad» en ISO-8859-1 con una ñ: no es UTF-8 válido.
    byte[] latin1 = small().text(UNIVERSITIES).replace("Ejemplo", "España").getBytes(StandardCharsets.ISO_8859_1);
    assertFileError(validate(small().setBytes(UNIVERSITIES, latin1)), "F1", UNIVERSITIES);
  }

  @Test
  @DisplayName("F2: cabecera incorrecta")
  void f2WrongHeader() {
    ValidationReport report = validate(small().replace(UNIVERSITIES, "university_id,name", "university_id,nombre"));
    assertError(report, "F2", UNIVERSITIES, 1, null);
  }

  @Test
  @DisplayName("F3: fila con un número de columnas distinto al de la cabecera")
  void f3WrongColumnCount() {
    ValidationReport report =
        validate(small().replace(UNIVERSITIES, "https://uej.example/\n", "https://uej.example/,sobra\n"));
    assertError(report, "F3", UNIVERSITIES, 2, null);
  }

  @Test
  @DisplayName("F4: identificador con formato no válido")
  void f4InvalidIdentifier() {
    assertError(validate(small().replace(CYCLES, "CF-EJ,Ciclo", "cf-ej,Ciclo")), "F4", CYCLES, 2, "cycle_id");
    assertError(validate(small().replace(CYCLES, "CF-EJ,Ciclo", "CF_EJ,Ciclo")), "F4", CYCLES, 2, "cycle_id");
    assertError(validate(small().replace(CYCLES, "CF-EJ,Ciclo", ",Ciclo")), "F4", CYCLES, 2, "cycle_id");
  }

  @Test
  @DisplayName("F5: valor de lista no permitido")
  void f5InvalidEnumValue() {
    assertError(validate(small().replace(UNIVERSITIES, ",PUBLICA,", ",ESTATAL,")), "F5", UNIVERSITIES, 2, "ownership");
    assertError(validate(small().replace(DEGREES, ",SIMPLE,", ",TRIPLE,")), "F5", DEGREES, 2, "degree_type");
    assertError(validate(small().replace(SUBJECTS, ",BASICA,", ",basica,")), "F5", SUBJECTS, 2, "subject_type");
    assertError(validate(small().replace(SOURCES, ",CONVENIO,", ",PDF,")), "F5", SOURCES, 2, "document_type");
    assertError(validate(small().replace(TABLES, ",VIGENTE,", ",ACTIVA,")), "F5", TABLES, 2, "status");
    assertError(validate(small().replace(RULES, ",SUBJECT,", ",ASIGNATURA,")), "F5", RULES, 2, "target_type");
    assertError(validate(small().replace(RULES, ",false,", ",no,")), "F5", RULES, 2, "is_conditional");
  }

  @Test
  @DisplayName("F6: decimales, enteros, fechas, SHA-256 o longitudes con formato incorrecto")
  void f6InvalidFormats() {
    assertError(validate(small().replace(MODULES, ",14,1", ",\"14,5\",1")), "F6", MODULES, 2, "ects");
    assertError(validate(small().replace(MODULES, ",14,1", ",14.555,1")), "F6", MODULES, 2, "ects");
    assertError(validate(small().replace(MODULES, ",14,1", ",-14,1")), "F6", MODULES, 2, "ects");
    assertError(validate(small().replace(MODULES, ",14,1", ",14,primero")), "F6", MODULES, 2, "course_year");
    assertError(validate(small().replace(SOURCES, "2025-03-14", "14/03/2025")), "F6", SOURCES, 2, "publication_date");
    assertError(validate(small().replace(SOURCES, ",,2026-09-01", ",ABC123,2026-09-01")), "F6", SOURCES, 2, "file_sha256");
    assertError(validate(small().replace(RULES, RULE_1_TAIL, ",,tres,Módulo")), "F6", RULES, 2, "source_page");
    assertError(
        validate(small().replace(UNIVERSITIES, "Universidad de Ejemplo", "Universidad de Ejemplo " + "x".repeat(250))),
        "F6",
        UNIVERSITIES,
        2,
        "name");
    // help_text admite hasta 500 caracteres.
    assertThat(validate(small().replace(CYCLES, HELP_TEXT, "x".repeat(500))).errors()).isEmpty();
    assertError(validate(small().replace(CYCLES, HELP_TEXT, "x".repeat(501))), "F6", CYCLES, 2, "help_text");
    assertManifestError(validate(small().replace(MANIFEST, "\"2025-10-01\"", "\"01/10/2025\"")), "F6", "date");
    assertManifestError(validate(small().replace(MANIFEST, "\"2025-26\"", "\"2025\"")), "F6", "academic_year");
  }

  @Test
  @DisplayName("F7: columna obligatoria vacía")
  void f7RequiredValueMissing() {
    assertError(validate(small().replace(UNIVERSITIES, "UEJ,Universidad de Ejemplo,UEJ", "UEJ,,UEJ")), "F7", UNIVERSITIES, 2, "name");
    assertError(validate(small().replace(MODULES, "CF-EJ-M01,CF-EJ,", "CF-EJ-M01,,")), "F7", MODULES, 2, "cycle_id");
    assertError(
        validate(small().replace(RULES, "SUBJECT,UEJ-GP-PROG1,", "SUBJECT,,")), "F7", RULES, 2, "target_subject_id");
    assertManifestError(validate(small().replace(MANIFEST, "\"Revisor Ficticio\"", "\"\"")), "F7", "reviewer");
  }

  @Test
  @DisplayName("F8: checksum, curso de las tablas o nombre de carpeta que no coinciden con el manifiesto")
  void f8ManifestMismatch() {
    ValidationReport tampered = validate(small().withoutResigning().replace(MODULES, "Programación de Ejemplo", "Otra cosa"));
    assertManifestError(tampered, "F8", "files");
    assertThat(tampered.errors()).anyMatch(i -> i.message().contains("checksum de " + MODULES));

    assertError(validate(small().replace(TABLES, ",2025-26,VIGENTE", ",2024-25,VIGENTE")), "F8", TABLES, 2, "academic_year");
    assertManifestError(validate(small().directoryName("2030-31")), "F8", "academic_year");
    assertManifestError(
        validate(small().withoutResigning().replace(MANIFEST, "\"name\": \"universities.csv\"", "\"name\": \"otro.csv\"")),
        "F8",
        "files");
    // En un ZIP no hay carpeta con la que comparar.
    assertThat(validate(small().directoryName(null)).errors()).isEmpty();
  }

  @Test
  @DisplayName("F9: regla OPTATIVE_CREDITS con asignatura o sin créditos")
  void f9OptativeCreditsShape() {
    assertError(
        validate(small().replace(RULES, "OPTATIVE_CREDITS,,6,", "OPTATIVE_CREDITS,,,")), "F9", RULES, 4, "recognized_ects");
    assertError(
        validate(small().replace(RULES, "OPTATIVE_CREDITS,,6,", "OPTATIVE_CREDITS,UEJ-GP-BD,6,")),
        "F9",
        RULES,
        4,
        "target_subject_id");
  }

  @Test
  @DisplayName("F10: tabla SIN_CONVENIO con reglas")
  void f10NoAgreementTableWithRules() {
    assertError(validate(small().replace(TABLES, "VIGENTE,S-EJ-01", "SIN_CONVENIO,S-EJ-01")), "F10", RULES, 2, "table_id");
  }

  @Test
  @DisplayName("F11: fragmento literal de más de 200 caracteres")
  void f11ExcerptTooLong() {
    assertError(
        validate(small().replace(RULES, "Módulo Ficticio 01 - Programación I", "x".repeat(201))), "F11", RULES, 2, "source_excerpt");
    assertThat(validate(small().replace(RULES, "Módulo Ficticio 01 - Programación I", "ñ".repeat(200))).errors()).isEmpty();
  }

  // ---------- A1 a A3: avisos, que no bloquean ----------

  @Test
  @DisplayName("A1: regla condicionada sin condición; es un aviso, no un error")
  void a1ConditionalRuleWithoutText() {
    ValidationReport report =
        validate(small().replace(RULES, "true,Solo si las prácticas son de naturaleza similar,", "true,,"));
    assertWarning(report, "A1", RULES, 5, "condition_text");
    assertThat(report.isValid()).isTrue();
  }

  @Test
  @DisplayName("A2: regla sin módulos; es un aviso, no un error")
  void a2RuleWithoutModules() {
    ValidationReport report = validate(small().replace(REQUIREMENTS, "R-003,CF-EJ-M04\n", ""));
    assertWarning(report, "A2", RULES, 4, "rule_id");
    assertThat(report.isValid()).isTrue();
  }

  @Test
  @DisplayName("A3: dos reglas alternativas con ECTS distintos para la misma asignatura; es un aviso")
  void a3AlternativeRulesWithDifferentEcts() {
    ValidationReport report =
        validate(
            small()
                .append(RULES, "R-099,T-EJ-01,SUBJECT,UEJ-GP-PROG1,4.5,false,,,3,Alternativa de prueba")
                .append(REQUIREMENTS, "R-099,CF-EJ-M02"));
    assertWarning(report, "A3", RULES, 8, "recognized_ects");
    assertThat(report.warnings().get(0).message()).contains("6 ECTS").contains("4.5");
    assertThat(report.isValid()).isTrue();

    // Con los mismos ECTS (aunque uno venga de la regla y otro de la asignatura) no hay aviso.
    ValidationReport same =
        validate(
            small()
                .append(RULES, "R-099,T-EJ-01,SUBJECT,UEJ-GP-PROG1,6.0,false,,,3,Alternativa de prueba")
                .append(REQUIREMENTS, "R-099,CF-EJ-M02"));
    assertThat(same.warnings()).isEmpty();
  }

  @Test
  @DisplayName("El dataset 2026-27 produce exactamente sus dos avisos; el 2025-26, ninguno")
  void sampleDatasetWarnings() {
    assertThat(validate(small()).warnings()).isEmpty();
    ValidationReport report = validate(full());
    assertThat(report.warnings()).hasSize(2);
    assertWarning(report, "A2", RULES, 53, "rule_id");
    assertWarning(report, "A1", RULES, 55, "condition_text");
  }

  // ---------- lectura desde ZIP ----------

  @Test
  @DisplayName("Un ZIP con los ficheros en la raíz o dentro de una carpeta se lee igual")
  void zipIsReadFromRootOrFolder() throws IOException {
    for (String folder : new String[] {null, "2025-26", "datos/2025-26"}) {
      DatasetFiles files = DatasetFiles.fromZip(new ByteArrayInputStream(small().toZip(folder)));
      assertThat(files.directoryName()).isNull();
      assertThat(files.files()).hasSize(10);
      assertThat(validator.validate(files).report().errors()).isEmpty();
    }
  }

  @Test
  @DisplayName("Un ZIP ignora ficheros ajenos y rechaza un fichero repetido")
  void zipIgnoresUnknownFilesAndRejectsDuplicates() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("carpeta/"));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("carpeta/LEEME.txt"));
      zip.write("no forma parte del contrato".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("carpeta/universities.csv"));
      zip.write(small().text(UNIVERSITIES).getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    DatasetFiles files = DatasetFiles.fromZip(new ByteArrayInputStream(bytes.toByteArray()));
    assertThat(files.files()).containsOnlyKeys(UNIVERSITIES);
    assertThat(validator.validate(files).report().errors()).extracting(Issue::code).contains("F1");

    ByteArrayOutputStream duplicated = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(duplicated)) {
      for (String name : List.of("a/universities.csv", "b/universities.csv")) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(1);
        zip.closeEntry();
      }
    }
    assertThatThrownBy(() -> DatasetFiles.fromZip(new ByteArrayInputStream(duplicated.toByteArray())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dos veces");
  }

  // ---------- utilidades ----------

  /** Curso 2025-26: una universidad, un ciclo, un grado y una tabla con 6 reglas. */
  private static SampleFiles small() {
    return SampleFiles.of("2025-26");
  }

  /** Curso 2026-27: completo. */
  private static SampleFiles full() {
    return SampleFiles.of("2026-27");
  }

  private ValidationReport validate(SampleFiles files) {
    return validator.validate(files.build()).report();
  }

  private static void assertError(ValidationReport report, String code, String file, long row, String column) {
    assertThat(report.errors())
        .as("errores: " + report.errors())
        .anyMatch(i -> matches(i, code, file, row, column) && i.severity() == Severity.ERROR);
    assertThat(report.isValid()).isFalse();
  }

  private static void assertWarning(ValidationReport report, String code, String file, long row, String column) {
    assertThat(report.errors()).as("no debe haber errores").isEmpty();
    assertThat(report.warnings())
        .as("avisos: " + report.warnings())
        .anyMatch(i -> matches(i, code, file, row, column) && i.severity() == Severity.WARNING);
  }

  private static void assertFileError(ValidationReport report, String code, String file) {
    assertThat(report.errors())
        .as("errores: " + report.errors())
        .anyMatch(i -> i.code().equals(code) && i.file().equals(file) && i.row() == null);
  }

  private static void assertManifestError(ValidationReport report, String code, String field) {
    assertThat(report.errors())
        .as("errores: " + report.errors())
        .anyMatch(i -> i.code().equals(code) && i.file().equals(MANIFEST) && field.equals(i.column()));
  }

  private static boolean matches(Issue issue, String code, String file, long row, String column) {
    return issue.code().equals(code)
        && issue.file().equals(file)
        && issue.row() != null
        && issue.row() == row
        && java.util.Objects.equals(issue.column(), column);
  }
}
