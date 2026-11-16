package es.convalidafp.ingestion;

import static es.convalidafp.ingestion.DatasetContract.CYCLES;
import static es.convalidafp.ingestion.DatasetContract.DEGREES;
import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.REQUIREMENTS;
import static es.convalidafp.ingestion.DatasetContract.RULES;
import static es.convalidafp.ingestion.DatasetContract.SOURCES;
import static es.convalidafp.ingestion.DatasetContract.SUBJECTS;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static es.convalidafp.ingestion.DatasetContract.UNIVERSITIES;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.Degree;
import es.convalidafp.catalog.DegreeSubject;
import es.convalidafp.catalog.FpCycle;
import es.convalidafp.catalog.FpModule;
import es.convalidafp.catalog.University;
import es.convalidafp.ingestion.RawDataset.DataRow;
import es.convalidafp.rules.RecognitionRule;
import es.convalidafp.rules.RecognitionTable;
import es.convalidafp.rules.SourceDocument;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Guarda un dataset ya validado como una versión nueva en borrador. No valida nada: solo debe
 * recibir datasets cuyo informe no tenga errores.
 */
@Component
class DatasetPersister {

  private final EntityManager em;

  DatasetPersister(EntityManager em) {
    this.em = em;
  }

  DatasetVersion persistAsDraft(RawDataset data, Instant now) {
    DatasetVersion version = DatasetVersion.draft(data.academicYear(), data.manifestDate(), data.reviewer(), now);
    em.persist(version);

    Map<String, University> universities = new HashMap<>();
    for (DataRow r : data.rows(UNIVERSITIES)) {
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
    for (DataRow r : data.rows(CYCLES)) {
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
    for (DataRow r : data.rows(MODULES)) {
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
    for (DataRow r : data.rows(DEGREES)) {
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
    for (DataRow r : data.rows(SUBJECTS)) {
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
    for (DataRow r : data.rows(SOURCES)) {
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
    for (DataRow r : data.rows(TABLES)) {
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

    Map<String, RecognitionRule> rules = new LinkedHashMap<>();
    for (DataRow r : data.rows(RULES)) {
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
    for (DataRow r : data.rows(REQUIREMENTS)) {
      rules.get(r.get("rule_id")).require(modules.get(r.get("module_id")));
    }
    rules.values().forEach(em::persist);

    em.flush();
    return version;
  }

  /** Celda vacía = desconocido = NULL. */
  private static String text(DataRow r, String column) {
    return r.blank(column) ? null : r.get(column);
  }

  private static BigDecimal decimal(DataRow r, String column) {
    return r.blank(column) ? null : new BigDecimal(r.get(column));
  }

  private static Integer integer(DataRow r, String column) {
    return r.blank(column) ? null : Integer.valueOf(r.get(column));
  }

  private static LocalDate date(DataRow r, String column) {
    return r.blank(column) ? null : LocalDate.parse(r.get(column));
  }
}
