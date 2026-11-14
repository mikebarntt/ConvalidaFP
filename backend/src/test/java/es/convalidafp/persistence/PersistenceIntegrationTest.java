package es.convalidafp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.catalog.Degree;
import es.convalidafp.catalog.DegreeRepository;
import es.convalidafp.catalog.DegreeSubject;
import es.convalidafp.catalog.DegreeSubjectRepository;
import es.convalidafp.catalog.FpCycle;
import es.convalidafp.catalog.FpCycleRepository;
import es.convalidafp.catalog.FpModule;
import es.convalidafp.catalog.FpModuleRepository;
import es.convalidafp.catalog.University;
import es.convalidafp.catalog.UniversityRepository;
import es.convalidafp.contract.Dataset;
import es.convalidafp.rules.RecognitionRule;
import es.convalidafp.rules.RecognitionRuleRepository;
import es.convalidafp.rules.RecognitionTable;
import es.convalidafp.rules.RecognitionTableRepository;
import es.convalidafp.rules.SourceDocumentRepository;
import es.convalidafp.testsupport.SampleDatasetLoader;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fase 1: el esquema, las entidades y los repositorios, contra una MariaDB real, cargando el
 * dataset de ejemplo. Cada test corre en una transacción que se deshace al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PersistenceIntegrationTest {

  @Autowired private EntityManager em;
  @Autowired private DatasetVersionRepository versions;
  @Autowired private UniversityRepository universities;
  @Autowired private FpCycleRepository cycles;
  @Autowired private FpModuleRepository modules;
  @Autowired private DegreeRepository degrees;
  @Autowired private DegreeSubjectRepository subjects;
  @Autowired private SourceDocumentRepository sources;
  @Autowired private RecognitionTableRepository tables;
  @Autowired private RecognitionRuleRepository rules;

  private Long previousId;
  private Long currentId;

  @BeforeEach
  void loadSampleDataset() {
    SampleDatasetLoader loader = new SampleDatasetLoader(em);
    DatasetVersion previous = loader.load("2025-26");
    DatasetVersion current = loader.load("2026-27");
    previous.publish(Instant.now());
    current.publish(Instant.now());
    em.flush();
    em.clear();
    previousId = previous.getId();
    currentId = current.getId();
  }

  // ---------- carga completa ----------

  @Test
  @DisplayName("Se guardan todas las filas de los 9 ficheros, en las dos versiones")
  void everyRowOfTheSampleDatasetIsStored() {
    for (var entry : List.of(new Object[] {"2025-26", previousId}, new Object[] {"2026-27", currentId})) {
      Dataset csv = SampleDatasetLoader.read((String) entry[0]);
      Long id = (Long) entry[1];
      assertThat(universities.countByDatasetVersionId(id)).isEqualTo(csv.rows("universities.csv").size());
      assertThat(cycles.countByDatasetVersionId(id)).isEqualTo(csv.rows("fp_cycles.csv").size());
      assertThat(modules.countByDatasetVersionId(id)).isEqualTo(csv.rows("fp_modules.csv").size());
      assertThat(degrees.countByDatasetVersionId(id)).isEqualTo(csv.rows("degrees.csv").size());
      assertThat(subjects.countByDatasetVersionId(id)).isEqualTo(csv.rows("degree_subjects.csv").size());
      assertThat(sources.countByDatasetVersionId(id)).isEqualTo(csv.rows("source_documents.csv").size());
      assertThat(tables.countByDatasetVersionId(id)).isEqualTo(csv.rows("recognition_tables.csv").size());
      assertThat(rules.countByDatasetVersionId(id)).isEqualTo(csv.rows("recognition_rules.csv").size());
      assertThat(requirementCount(id)).isEqualTo(csv.rows("rule_requirements.csv").size());
    }
  }

  @Test
  @DisplayName("Tildes, comillas y comas se guardan tal cual (caso 15)")
  void accentsQuotesAndCommasSurviveTheRoundTrip() {
    assertThat(universities.findByDatasetVersionIdAndCode(currentId, "UFI").orElseThrow().getName())
        .isEqualTo("Universidad Ficticia de Tecnología \"San Ejemplo\", Campus Norte");
  }

  @Test
  @DisplayName("Celda vacía = desconocido = NULL, nunca 0")
  void unknownValuesAreStoredAsNull() {
    DegreeSubject subject = subjects.findByDatasetVersionIdAndCode(currentId, "UFI-GS-SIS").orElseThrow();
    assertThat(subject.getEcts()).isNull();
    assertThat(subjects.findByDatasetVersionIdAndCode(currentId, "UFI-GS-SW").orElseThrow().getCourseYear()).isNull();
  }

  // ---------- ECTS ----------

  @Test
  @DisplayName("Los ECTS son DECIMAL y BigDecimal, sin errores de redondeo (caso 13)")
  void ectsAreExactDecimals() {
    BigDecimal lm = subjects.findByDatasetVersionIdAndCode(currentId, "UEJ-GP-LM").orElseThrow().getEcts();
    BigDecimal ipo = subjects.findByDatasetVersionIdAndCode(currentId, "UEJ-GP-IPO").orElseThrow().getEcts();
    assertThat(lm).isEqualByComparingTo("4.5");
    assertThat(lm.add(ipo)).isEqualByComparingTo("9");
    assertThat(modules.findByDatasetVersionIdAndCode(currentId, "CF-EJ-M07").orElseThrow().getEcts())
        .isEqualByComparingTo("12.5");
    assertThat(degrees.findByDatasetVersionIdAndCode(currentId, "UEJ-DG").orElseThrow().getTotalEcts())
        .isEqualByComparingTo("372");
    assertThat(columnType("degree_subject", "ects")).isEqualTo("decimal(6,2)");
    assertThat(columnType("recognition_table", "table_max_ects")).isEqualTo("decimal(6,2)");
    assertThat(columnType("recognition_rule", "recognized_ects")).isEqualTo("decimal(6,2)");
  }

  @Test
  @DisplayName("La base de datos rechaza ECTS que no sean mayores que 0")
  void zeroEctsAreRejected() {
    DatasetVersion version = versions.findById(currentId).orElseThrow();
    FpCycle cycle = cycles.findByDatasetVersionIdAndCode(currentId, "CF-EJ").orElseThrow();
    FpModule zero = new FpModule(version, "CF-EJ-CERO", cycle, null, "Módulo Ficticio Cero", BigDecimal.ZERO, 1);
    assertThatThrownBy(() -> modules.saveAndFlush(zero)).isInstanceOf(DataAccessException.class);
  }

  // ---------- claves ----------

  @Test
  @DisplayName("Un identificador no se repite dentro de una versión")
  void codesAreUniqueWithinAVersion() {
    DatasetVersion version = versions.findById(currentId).orElseThrow();
    University duplicate =
        new University(version, "UEJ", "Universidad de Ejemplo Duplicada", null, University.Ownership.PUBLICA, null, null);
    assertThatThrownBy(() -> universities.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("El mismo identificador existe en dos versiones: es estable entre cursos")
  void sameCodeLivesInBothVersions() {
    assertThat(degrees.findByDatasetVersionIdAndCode(previousId, "UEJ-GP")).isPresent();
    assertThat(degrees.findByDatasetVersionIdAndCode(currentId, "UEJ-GP")).isPresent();
    assertThat(degrees.findByDatasetVersionIdAndCode(previousId, "UPR-GD")).isEmpty();
  }

  @Test
  @DisplayName("rule_requirement tiene clave compuesta (regla, módulo)")
  void ruleRequirementHasCompositeKey() {
    RecognitionRule rule = rules.findByDatasetVersionIdAndCode(currentId, "R-001").orElseThrow();
    FpModule module = modules.findByDatasetVersionIdAndCode(currentId, "CF-EJ-M01").orElseThrow();
    assertThat(primaryKeyColumns("rule_requirement")).containsExactly("rule_id", "module_id");
    assertThatThrownBy(
            () ->
                em.createNativeQuery(
                        "INSERT INTO rule_requirement (rule_id, module_id, dataset_version_id) VALUES (?, ?, ?)")
                    .setParameter(1, rule.getId())
                    .setParameter(2, module.getId())
                    .setParameter(3, currentId)
                    .executeUpdate())
        .isInstanceOf(PersistenceException.class);
  }

  @Test
  @DisplayName("Una sola tabla por combinación de ciclo, grado y curso")
  void oneTablePerCycleDegreeAndYear() {
    RecognitionTable existing = tables.findByDatasetVersionIdAndCode(currentId, "T-EJ-01").orElseThrow();
    RecognitionTable duplicate =
        new RecognitionTable(
            existing.getDatasetVersion(),
            "T-EJ-99",
            existing.getCycle(),
            existing.getDegree(),
            "2026-27",
            RecognitionTable.Status.SIN_CONVENIO,
            null,
            null,
            null);
    assertThatThrownBy(() -> tables.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------- relaciones ----------

  @Test
  @DisplayName("Una regla conoce sus módulos: Y (caso 2), O (caso 3) y regla sin módulos")
  void rulesKnowTheirRequiredModules() {
    RecognitionTable table = tables.findByDatasetVersionIdAndCode(currentId, "T-EJ-01").orElseThrow();
    List<RecognitionRule> tableRules = rules.findByRecognitionTableIdOrderByCode(table.getId());
    assertThat(tableRules).extracting(RecognitionRule::getCode).isSorted().hasSize(11);

    assertThat(moduleCodes(rule(tableRules, "R-002"))).containsExactlyInAnyOrder("CF-EJ-M02", "CF-EJ-M03");
    assertThat(rule(tableRules, "R-006").getTargetSubject().getCode()).isEqualTo("UEJ-GP-WEB");
    assertThat(rule(tableRules, "R-007").getTargetSubject().getCode()).isEqualTo("UEJ-GP-WEB");
    assertThat(rule(tableRules, "R-004").isConditional()).isTrue();
    assertThat(rule(tableRules, "R-003").getTargetSubject()).isNull();
    assertThat(rule(tableRules, "R-003").getRecognizedEcts()).isEqualByComparingTo("6");

    RecognitionRule byTitle = rules.findByDatasetVersionIdAndCode(currentId, "R-052").orElseThrow();
    assertThat(byTitle.getRequirements()).isEmpty();
  }

  @Test
  @DisplayName("Fuente efectiva: la propia de la regla o, si no tiene, la de su tabla")
  void effectiveSourceIsTheRuleSourceOrTheTableSource() {
    RecognitionRule own = rules.findByDatasetVersionIdAndCode(currentId, "R-053").orElseThrow();
    assertThat(own.getSource().getCode()).isEqualTo("S-EJ-04");
    assertThat(own.getEffectiveSource().getCode()).isEqualTo("S-EJ-04");

    RecognitionRule inherited = rules.findByDatasetVersionIdAndCode(currentId, "R-001").orElseThrow();
    assertThat(inherited.getSource()).isNull();
    assertThat(inherited.getEffectiveSource().getCode()).isEqualTo("S-EJ-01");
  }

  @Test
  @DisplayName("Tabla por ciclo y grado: VIGENTE, SIN_CONVENIO y sin datos (caso 1); tope de tabla (caso 6)")
  void tablesAreFoundByCycleAndDegree() {
    RecognitionTable active =
        tables.findByDatasetVersionIdAndCycleCodeAndDegreeCode(currentId, "CF-PR", "UPR-GD").orElseThrow();
    assertThat(active.getStatus()).isEqualTo(RecognitionTable.Status.VIGENTE);
    assertThat(active.getTableMaxEcts()).isEqualByComparingTo("30");
    assertThat(active.getSource().getCode()).isEqualTo("S-PR-01");

    RecognitionTable noAgreement =
        tables.findByDatasetVersionIdAndCycleCodeAndDegreeCode(currentId, "CF-PR", "UEJ-DG").orElseThrow();
    assertThat(noAgreement.getStatus()).isEqualTo(RecognitionTable.Status.SIN_CONVENIO);
    assertThat(noAgreement.getSource()).isNull();

    assertThat(tables.findByDatasetVersionIdAndCycleCodeAndDegreeCode(currentId, "CF-PR", "UEJ-GP")).isEmpty();
    assertThat(tables.findByDatasetVersionIdAndCycleCodeOrderByCode(currentId, "CF-EJ")).hasSize(5);
  }

  @Test
  @DisplayName("Catálogo: módulos de un ciclo, grados de una universidad y asignaturas de un grado")
  void catalogRelationsAreNavigable() {
    FpCycle cycle = cycles.findByDatasetVersionIdAndCode(currentId, "CF-EJ-2024").orElseThrow();
    assertThat(modules.findByCycleIdOrderByCode(cycle.getId())).hasSize(14);

    University university = universities.findByDatasetVersionIdAndCode(currentId, "UEJ").orElseThrow();
    assertThat(degrees.findByUniversityIdOrderByCode(university.getId()))
        .extracting(Degree::getCode)
        .containsExactly("UEJ-DG", "UEJ-GP", "UEJ-GP-SUR");

    Degree degree = degrees.findByDatasetVersionIdAndCode(currentId, "UEJ-GP").orElseThrow();
    List<DegreeSubject> degreeSubjects = subjects.findByDegreeIdOrderByCourseYearAscCodeAsc(degree.getId());
    assertThat(degreeSubjects).hasSize(40);
    assertThat(degreeSubjects.stream().map(DegreeSubject::getEcts).reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(degree.getTotalEcts());
    assertThat(degree.getUniversity().getCode()).isEqualTo("UEJ");
  }

  @Test
  @DisplayName("La base de datos impide apuntar a datos de otra versión")
  void referencesAcrossVersionsAreRejected() {
    DatasetVersion current = versions.findById(currentId).orElseThrow();
    Degree degreeOfPreviousVersion = degrees.findByDatasetVersionIdAndCode(previousId, "UEJ-GP").orElseThrow();
    DegreeSubject mixed =
        new DegreeSubject(
            current,
            "UEJ-GP-MEZCLA",
            degreeOfPreviousVersion,
            "Asignatura de Prueba Mezclada",
            new BigDecimal("6"),
            DegreeSubject.SubjectType.OBLIGATORIA,
            1,
            null);
    assertThatThrownBy(() -> subjects.saveAndFlush(mixed)).isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------- versiones ----------

  @Test
  @DisplayName("Cada curso es una versión; el curso vigente es el publicado más reciente")
  void currentPublishedVersionIsTheLatestYear() {
    assertThat(versions.findFirstByStatusOrderByAcademicYearDesc(Status.PUBLICADO).orElseThrow().getId())
        .isEqualTo(currentId);
    DatasetVersion previous = versions.findByAcademicYearAndStatus("2025-26", Status.PUBLICADO).orElseThrow();
    assertThat(previous.getId()).isEqualTo(previousId);
    assertThat(previous.isPublished()).isTrue();
    assertThat(previous.getPublishedAt()).isNotNull();
    assertThat(versions.findByAcademicYearAndStatus("2026-27", Status.BORRADOR)).isEmpty();
  }

  @Test
  @DisplayName("Solo una versión publicada por curso; borradores, los que hagan falta")
  void onlyOnePublishedVersionPerYear() {
    DatasetVersion firstDraft = versions.saveAndFlush(draft("2026-27"));
    DatasetVersion secondDraft = versions.saveAndFlush(draft("2026-27"));
    assertThat(versions.findByAcademicYearOrderByIdAsc("2026-27"))
        .extracting(DatasetVersion::getId)
        .containsExactly(currentId, firstDraft.getId(), secondDraft.getId());

    secondDraft.publish(Instant.now());
    assertThatThrownBy(() -> versions.saveAndFlush(secondDraft))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Una versión nace en borrador y no se publica dos veces")
  void versionLifecycle() {
    DatasetVersion version = draft("2027-28");
    assertThat(version.getStatus()).isEqualTo(Status.BORRADOR);
    assertThat(version.getPublishedAt()).isNull();
    version.publish(Instant.now());
    assertThat(version.isPublished()).isTrue();
    assertThatThrownBy(() -> version.publish(Instant.now())).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> draft("2027")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Una lectura fijada a una versión no ve las demás (casos 11 y 14)")
  void aReadPinnedToOneVersionIgnoresTheOthers() {
    // Un lector resuelve la versión vigente una sola vez...
    Long pinned = versions.findFirstByStatusOrderByAcademicYearDesc(Status.PUBLICADO).orElseThrow().getId();
    long subjectsBefore = subjects.countByDatasetVersionId(pinned);
    long rulesBefore = rules.countByDatasetVersionId(pinned);

    // ...y mientras tanto se carga otra versión completa del mismo curso.
    DatasetVersion draft = new SampleDatasetLoader(em).load("2026-27");
    em.clear();

    assertThat(draft.getId()).isNotEqualTo(pinned);
    assertThat(subjects.countByDatasetVersionId(pinned)).isEqualTo(subjectsBefore);
    assertThat(rules.countByDatasetVersionId(pinned)).isEqualTo(rulesBefore);
    assertThat(subjects.countByDatasetVersionId(draft.getId())).isEqualTo(subjectsBefore);

    // La asignatura renombrada conserva un nombre por curso, sin sobrescribir el anterior.
    assertThat(subjects.findByDatasetVersionIdAndCode(previousId, "UEJ-GP-WEB").orElseThrow().getName())
        .isEqualTo("Programación Web de Prueba");
    assertThat(subjects.findByDatasetVersionIdAndCode(pinned, "UEJ-GP-WEB").orElseThrow().getName())
        .isEqualTo("Desarrollo Web de Prueba");
    assertThat(rules.countByDatasetVersionId(previousId)).isEqualTo(6);
  }

  // ---------- utilidades ----------

  private static DatasetVersion draft(String academicYear) {
    return DatasetVersion.draft(academicYear, LocalDate.of(2026, 11, 13), "Revisor Ficticio", Instant.now());
  }

  private static RecognitionRule rule(List<RecognitionRule> rules, String code) {
    return rules.stream().filter(r -> r.getCode().equals(code)).findFirst().orElseThrow();
  }

  private static List<String> moduleCodes(RecognitionRule rule) {
    return rule.getRequirements().stream().map(r -> r.getModule().getCode()).toList();
  }

  private long requirementCount(Long versionId) {
    return em.createQuery("select count(r) from RuleRequirement r where r.datasetVersion.id = :id", Long.class)
        .setParameter("id", versionId)
        .getSingleResult();
  }

  private String columnType(String table, String column) {
    return (String)
        em.createNativeQuery(
                "SELECT column_type FROM information_schema.columns"
                    + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")
            .setParameter(1, table)
            .setParameter(2, column)
            .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  private List<String> primaryKeyColumns(String table) {
    return em.createNativeQuery(
            "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE table_schema = DATABASE() AND table_name = ? AND constraint_name = 'PRIMARY'"
                + " ORDER BY ordinal_position")
        .setParameter(1, table)
        .getResultList();
  }
}
