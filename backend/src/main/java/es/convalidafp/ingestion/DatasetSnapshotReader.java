package es.convalidafp.ingestion;

import static es.convalidafp.ingestion.DatasetContract.CYCLES;
import static es.convalidafp.ingestion.DatasetContract.DEGREES;
import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.RULES;
import static es.convalidafp.ingestion.DatasetContract.SOURCES;
import static es.convalidafp.ingestion.DatasetContract.SUBJECTS;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static es.convalidafp.ingestion.DatasetContract.UNIVERSITIES;

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
import es.convalidafp.catalog.VersionedEntity;
import es.convalidafp.rules.RecognitionRule;
import es.convalidafp.rules.RecognitionRuleRepository;
import es.convalidafp.rules.RecognitionTable;
import es.convalidafp.rules.RecognitionTableRepository;
import es.convalidafp.rules.SourceDocument;
import es.convalidafp.rules.SourceDocumentRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Construye la foto comparable de una versión guardada, con las columnas del contrato. */
@Component
class DatasetSnapshotReader {

  private final UniversityRepository universities;
  private final FpCycleRepository cycles;
  private final FpModuleRepository modules;
  private final DegreeRepository degrees;
  private final DegreeSubjectRepository subjects;
  private final SourceDocumentRepository sources;
  private final RecognitionTableRepository tables;
  private final RecognitionRuleRepository rules;

  DatasetSnapshotReader(
      UniversityRepository universities,
      FpCycleRepository cycles,
      FpModuleRepository modules,
      DegreeRepository degrees,
      DegreeSubjectRepository subjects,
      SourceDocumentRepository sources,
      RecognitionTableRepository tables,
      RecognitionRuleRepository rules) {
    this.universities = universities;
    this.cycles = cycles;
    this.modules = modules;
    this.degrees = degrees;
    this.subjects = subjects;
    this.sources = sources;
    this.tables = tables;
    this.rules = rules;
  }

  /** Debe llamarse dentro de una transacción: recorre relaciones perezosas. */
  DatasetSnapshot read(Long versionId) {
    DatasetSnapshot snapshot = new DatasetSnapshot();
    for (University u : universities.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          UNIVERSITIES,
          u.getCode(),
          row(
              "name", u.getName(),
              "short_name", u.getShortName(),
              "ownership", u.getOwnership(),
              "region", u.getRegion(),
              "website_url", u.getWebsiteUrl()));
    }
    for (FpCycle c : cycles.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          CYCLES,
          c.getCode(),
          row(
              "name", c.getName(),
              "acronym", c.getAcronym(),
              "professional_family", c.getProfessionalFamily(),
              "regulation_ref", c.getRegulationRef(),
              "total_ects", c.getTotalEcts(),
              "help_text", c.getHelpText()));
    }
    for (FpModule m : modules.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          MODULES,
          m.getCode(),
          row(
              "cycle_id", code(m.getCycle()),
              "official_code", m.getOfficialCode(),
              "name", m.getName(),
              "ects", m.getEcts(),
              "course_year", m.getCourseYear()));
    }
    for (Degree d : degrees.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          DEGREES,
          d.getCode(),
          row(
              "university_id", code(d.getUniversity()),
              "name", d.getName(),
              "degree_type", d.getDegreeType(),
              "center", d.getCenter(),
              "total_ects", d.getTotalEcts(),
              "plan_code", d.getPlanCode(),
              "url", d.getUrl(),
              "subjects_complete", d.isSubjectsComplete()));
    }
    for (DegreeSubject s : subjects.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          SUBJECTS,
          s.getCode(),
          row(
              "degree_id", code(s.getDegree()),
              "name", s.getName(),
              "ects", s.getEcts(),
              "subject_type", s.getSubjectType(),
              "course_year", s.getCourseYear(),
              "official_code", s.getOfficialCode()));
    }
    for (SourceDocument s : sources.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          SOURCES,
          s.getCode(),
          row(
              "document_type", s.getDocumentType(),
              "title", s.getTitle(),
              "issuer", s.getIssuer(),
              "publication_date", s.getPublicationDate(),
              "url", s.getUrl(),
              "file_sha256", s.getFileSha256(),
              "retrieved_at", s.getRetrievedAt()));
    }
    for (RecognitionTable t : tables.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          TABLES,
          t.getCode(),
          row(
              "cycle_id", code(t.getCycle()),
              "degree_id", code(t.getDegree()),
              "academic_year", t.getAcademicYear(),
              "status", t.getStatus(),
              "source_id", code(t.getSource()),
              "table_max_ects", t.getTableMaxEcts(),
              "notes", t.getNotes()));
    }
    for (RecognitionRule r : rules.findByDatasetVersionIdOrderByCode(versionId)) {
      snapshot.put(
          RULES,
          r.getCode(),
          row(
              "table_id", code(r.getRecognitionTable()),
              "target_type", r.getTargetType(),
              "target_subject_id", code(r.getTargetSubject()),
              "recognized_ects", r.getRecognizedEcts(),
              "is_conditional", r.isConditional(),
              "condition_text", r.getConditionText(),
              "source_id", code(r.getSource()),
              "source_page", r.getSourcePage(),
              "source_excerpt", r.getSourceExcerpt(),
              DatasetDiff.REQUIRED_MODULES,
                  DatasetSnapshot.joinModules(
                      r.getRequirements().stream().map(q -> q.getModule().getCode()).collect(Collectors.toSet()))));
    }
    return snapshot;
  }

  private static String code(VersionedEntity entity) {
    return entity == null ? null : entity.getCode();
  }

  /** Pares columna, valor. Un valor null (desconocido) se guarda como cadena vacía. */
  private static Map<String, String> row(Object... columnsAndValues) {
    Map<String, String> row = new LinkedHashMap<>();
    for (int i = 0; i < columnsAndValues.length; i += 2) {
      Object value = columnsAndValues[i + 1];
      row.put((String) columnsAndValues[i], value == null ? "" : value.toString());
    }
    return row;
  }
}
