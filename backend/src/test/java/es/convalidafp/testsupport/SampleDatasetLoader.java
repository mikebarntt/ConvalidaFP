package es.convalidafp.testsupport;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.Degree;
import es.convalidafp.catalog.DegreeSubject;
import es.convalidafp.catalog.FpCycle;
import es.convalidafp.catalog.FpModule;
import es.convalidafp.catalog.University;
import es.convalidafp.contract.Dataset;
import es.convalidafp.contract.Dataset.Row;
import es.convalidafp.rules.RecognitionRule;
import es.convalidafp.rules.RecognitionTable;
import es.convalidafp.rules.SourceDocument;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Carga un curso del dataset de ejemplo en la base de datos como versión en borrador.
 *
 * <p>Es código de test de la Fase 1: da por bueno el dataset (ya lo comprueban los tests del
 * contrato). El importador real, con validación e informe de errores, es de la Fase 3.
 */
public final class SampleDatasetLoader {

  public static final Path SAMPLE =
      Path.of(System.getProperty("convalidafp.dataDir", "../data")).resolve("sample");

  private final EntityManager em;

  public SampleDatasetLoader(EntityManager em) {
    this.em = em;
  }

  public static Dataset read(String academicYear) {
    return Dataset.load(SAMPLE.resolve(academicYear));
  }

  public DatasetVersion load(String academicYear) {
    Dataset data = read(academicYear);
    DatasetVersion version =
        DatasetVersion.draft(academicYear, LocalDate.of(2026, 11, 13), "Revisor Ficticio", Instant.now());
    em.persist(version);

    Map<String, University> universities = new HashMap<>();
    for (Row r : data.rows("universities.csv")) {
      University university =
          new University(
              version,
              r.get("university_id"),
              r.get("name"),
              text(r, "short_name"),
              University.Ownership.valueOf(r.get("ownership")),
              text(r, "region"),
              text(r, "website_url"));
      em.persist(university);
      universities.put(university.getCode(), university);
    }

    Map<String, FpCycle> cycles = new HashMap<>();
    for (Row r : data.rows("fp_cycles.csv")) {
      FpCycle cycle =
          new FpCycle(
              version,
              r.get("cycle_id"),
              r.get("name"),
              text(r, "acronym"),
              text(r, "professional_family"),
              text(r, "regulation_ref"),
              decimal(r, "total_ects"),
              text(r, "help_text"));
      em.persist(cycle);
      cycles.put(cycle.getCode(), cycle);
    }

    Map<String, FpModule> modules = new HashMap<>();
    for (Row r : data.rows("fp_modules.csv")) {
      FpModule module =
          new FpModule(
              version,
              r.get("module_id"),
              cycles.get(r.get("cycle_id")),
              text(r, "official_code"),
              r.get("name"),
              decimal(r, "ects"),
              integer(r, "course_year"));
      em.persist(module);
      modules.put(module.getCode(), module);
    }

    Map<String, Degree> degrees = new HashMap<>();
    for (Row r : data.rows("degrees.csv")) {
      Degree degree =
          new Degree(
              version,
              r.get("degree_id"),
              universities.get(r.get("university_id")),
              r.get("name"),
              Degree.DegreeType.valueOf(r.get("degree_type")),
              text(r, "center"),
              decimal(r, "total_ects"),
              text(r, "plan_code"),
              text(r, "url"),
              "true".equals(r.get("subjects_complete")));
      em.persist(degree);
      degrees.put(degree.getCode(), degree);
    }

    Map<String, DegreeSubject> subjects = new HashMap<>();
    for (Row r : data.rows("degree_subjects.csv")) {
      DegreeSubject subject =
          new DegreeSubject(
              version,
              r.get("subject_id"),
              degrees.get(r.get("degree_id")),
              r.get("name"),
              decimal(r, "ects"),
              DegreeSubject.SubjectType.valueOf(r.get("subject_type")),
              integer(r, "course_year"),
              text(r, "official_code"));
      em.persist(subject);
      subjects.put(subject.getCode(), subject);
    }

    Map<String, SourceDocument> sources = new HashMap<>();
    for (Row r : data.rows("source_documents.csv")) {
      SourceDocument source =
          new SourceDocument(
              version,
              r.get("source_id"),
              SourceDocument.DocumentType.valueOf(r.get("document_type")),
              r.get("title"),
              text(r, "issuer"),
              date(r, "publication_date"),
              text(r, "url"),
              text(r, "file_sha256"),
              date(r, "retrieved_at"));
      em.persist(source);
      sources.put(source.getCode(), source);
    }

    Map<String, RecognitionTable> tables = new HashMap<>();
    for (Row r : data.rows("recognition_tables.csv")) {
      RecognitionTable table =
          new RecognitionTable(
              version,
              r.get("table_id"),
              cycles.get(r.get("cycle_id")),
              degrees.get(r.get("degree_id")),
              r.get("academic_year"),
              RecognitionTable.Status.valueOf(r.get("status")),
              sources.get(r.get("source_id")),
              decimal(r, "table_max_ects"),
              text(r, "notes"));
      em.persist(table);
      tables.put(table.getCode(), table);
    }

    Map<String, RecognitionRule> rules = new HashMap<>();
    for (Row r : data.rows("recognition_rules.csv")) {
      RecognitionRule rule =
          new RecognitionRule(
              version,
              r.get("rule_id"),
              tables.get(r.get("table_id")),
              RecognitionRule.TargetType.valueOf(r.get("target_type")),
              subjects.get(r.get("target_subject_id")),
              decimal(r, "recognized_ects"),
              Boolean.parseBoolean(r.get("is_conditional")),
              text(r, "condition_text"),
              sources.get(r.get("source_id")),
              Integer.parseInt(r.get("source_page")),
              text(r, "source_excerpt"));
      rules.put(rule.getCode(), rule);
    }
    for (Row r : data.rows("rule_requirements.csv")) {
      rules.get(r.get("rule_id")).require(modules.get(r.get("module_id")));
    }
    rules.values().forEach(em::persist);

    em.flush();
    return version;
  }

  /** Celda vacía = desconocido = NULL. */
  private static String text(Row r, String column) {
    return r.blank(column) ? null : r.get(column);
  }

  private static BigDecimal decimal(Row r, String column) {
    return r.blank(column) ? null : new BigDecimal(r.get(column));
  }

  private static Integer integer(Row r, String column) {
    return r.blank(column) ? null : Integer.valueOf(r.get(column));
  }

  private static LocalDate date(Row r, String column) {
    return r.blank(column) ? null : LocalDate.parse(r.get(column));
  }
}
