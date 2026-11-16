package es.convalidafp.ingestion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nombres de fichero y cabeceras exactas del contrato de datos (CONTRACT.md). */
public final class DatasetContract {

  public static final String MANIFEST = "manifest.json";

  public static final String UNIVERSITIES = "universities.csv";
  public static final String CYCLES = "fp_cycles.csv";
  public static final String MODULES = "fp_modules.csv";
  public static final String DEGREES = "degrees.csv";
  public static final String SUBJECTS = "degree_subjects.csv";
  public static final String SOURCES = "source_documents.csv";
  public static final String TABLES = "recognition_tables.csv";
  public static final String RULES = "recognition_rules.csv";
  public static final String REQUIREMENTS = "rule_requirements.csv";

  /** Los 9 ficheros CSV y su cabecera, en el orden del contrato. */
  public static final Map<String, List<String>> HEADERS;

  static {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(UNIVERSITIES, List.of("university_id", "name", "short_name", "ownership", "region", "website_url"));
    headers.put(CYCLES, List.of("cycle_id", "name", "acronym", "professional_family", "regulation_ref", "total_ects", "help_text"));
    headers.put(MODULES, List.of("module_id", "cycle_id", "official_code", "name", "ects", "course_year"));
    headers.put(
        DEGREES,
        List.of("degree_id", "university_id", "name", "degree_type", "center", "total_ects", "plan_code", "url"));
    headers.put(
        SUBJECTS,
        List.of("subject_id", "degree_id", "name", "ects", "subject_type", "course_year", "official_code"));
    headers.put(
        SOURCES,
        List.of(
            "source_id", "document_type", "title", "issuer", "publication_date", "url", "file_sha256", "retrieved_at"));
    headers.put(
        TABLES,
        List.of("table_id", "cycle_id", "degree_id", "academic_year", "status", "source_id", "table_max_ects", "notes"));
    headers.put(
        RULES,
        List.of(
            "rule_id",
            "table_id",
            "target_type",
            "target_subject_id",
            "recognized_ects",
            "is_conditional",
            "condition_text",
            "source_id",
            "source_page",
            "source_excerpt"));
    headers.put(REQUIREMENTS, List.of("rule_id", "module_id"));
    HEADERS = Collections.unmodifiableMap(headers);
  }

  private DatasetContract() {}
}
