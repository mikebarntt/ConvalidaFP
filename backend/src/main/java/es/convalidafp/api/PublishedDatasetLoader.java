package es.convalidafp.api;

import static es.convalidafp.api.Numbers.plain;

import es.convalidafp.api.CatalogDtos.CycleDto;
import es.convalidafp.api.CatalogDtos.DatasetInfo;
import es.convalidafp.api.CatalogDtos.DegreeDto;
import es.convalidafp.api.CatalogDtos.SourceDto;
import es.convalidafp.api.CatalogDtos.SubjectDto;
import es.convalidafp.api.CatalogDtos.UniversityDto;
import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.catalog.Degree;
import es.convalidafp.catalog.DegreeRepository;
import es.convalidafp.catalog.DegreeSubject;
import es.convalidafp.catalog.DegreeSubjectRepository;
import es.convalidafp.catalog.FpCycleRepository;
import es.convalidafp.catalog.UniversityRepository;
import es.convalidafp.rules.RecognitionRule;
import es.convalidafp.rules.RecognitionRuleRepository;
import es.convalidafp.rules.RecognitionTable;
import es.convalidafp.rules.RecognitionTableRepository;
import es.convalidafp.rules.SourceDocument;
import es.convalidafp.rules.SourceDocumentRepository;
import es.convalidafp.simulation.DegreeData;
import es.convalidafp.simulation.DegreeData.SubjectData;
import es.convalidafp.simulation.RuleData;
import es.convalidafp.simulation.SourceRef;
import es.convalidafp.simulation.TableData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de las entidades JPA a los datos del motor: lee una versión entera de la base de datos,
 * en una sola transacción, y la convierte en un {@link PublishedDataset} inmutable.
 *
 * <p>Es el único punto donde las entidades se traducen a {@code DegreeData}, {@code TableData} y
 * {@code RuleData}. El motor nunca ve una entidad.
 */
@Component
public class PublishedDatasetLoader {

  private final DatasetVersionRepository versions;
  private final FpCycleRepository cycles;
  private final UniversityRepository universities;
  private final DegreeRepository degrees;
  private final DegreeSubjectRepository subjects;
  private final SourceDocumentRepository sources;
  private final RecognitionTableRepository tables;
  private final RecognitionRuleRepository rules;

  PublishedDatasetLoader(
      DatasetVersionRepository versions,
      FpCycleRepository cycles,
      UniversityRepository universities,
      DegreeRepository degrees,
      DegreeSubjectRepository subjects,
      SourceDocumentRepository sources,
      RecognitionTableRepository tables,
      RecognitionRuleRepository rules) {
    this.versions = versions;
    this.cycles = cycles;
    this.universities = universities;
    this.degrees = degrees;
    this.subjects = subjects;
    this.sources = sources;
    this.tables = tables;
    this.rules = rules;
  }

  @Transactional(readOnly = true)
  public PublishedDataset load(Long versionId) {
    DatasetVersion version =
        versions
            .findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("No existe la versión " + versionId));
    DatasetInfo info = new DatasetInfo(version.getId(), version.getAcademicYear(), version.getPublishedAt());

    List<CycleDto> cycleDtos =
        cycles.findByDatasetVersionIdOrderByCode(versionId).stream()
            .map(
                c ->
                    new CycleDto(
                        c.getCode(),
                        c.getName(),
                        c.getAcronym(),
                        c.getProfessionalFamily(),
                        c.getRegulationRef(),
                        plain(c.getTotalEcts()),
                        c.getHelpText()))
            .toList();

    List<UniversityDto> universityDtos =
        universities.findByDatasetVersionIdOrderByCode(versionId).stream()
            .map(
                u ->
                    new UniversityDto(
                        u.getCode(),
                        u.getName(),
                        u.getShortName(),
                        u.getOwnership().name(),
                        u.getRegion(),
                        u.getWebsiteUrl()))
            .toList();

    // Asignaturas por grado: para la API (SubjectDto) y para el motor (SubjectData).
    Map<String, List<SubjectDto>> subjectDtos = new HashMap<>();
    Map<String, List<SubjectData>> subjectData = new HashMap<>();
    for (DegreeSubject s : subjects.findByDatasetVersionIdOrderByCode(versionId)) {
      String degreeId = s.getDegree().getCode();
      subjectDtos
          .computeIfAbsent(degreeId, k -> new ArrayList<>())
          .add(
              new SubjectDto(
                  s.getCode(),
                  s.getName(),
                  plain(s.getEcts()),
                  s.getSubjectType().name(),
                  s.getCourseYear(),
                  s.getOfficialCode()));
      subjectData
          .computeIfAbsent(degreeId, k -> new ArrayList<>())
          .add(
              new SubjectData(
                  s.getCode(), s.getName(), plain(s.getEcts()), s.getSubjectType().name(), s.getCourseYear()));
    }

    List<DegreeDto> degreeDtos = new ArrayList<>();
    Map<String, DegreeData> degreeData = new HashMap<>();
    for (Degree d : degrees.findByDatasetVersionIdOrderByCode(versionId)) {
      degreeDtos.add(
          new DegreeDto(
              d.getCode(),
              d.getUniversity().getCode(),
              d.getUniversity().getName(),
              d.getName(),
              d.getDegreeType().name(),
              d.getCenter(),
              plain(d.getTotalEcts()),
              d.getPlanCode(),
              d.getUrl(),
              null));
      degreeData.put(
          d.getCode(),
          new DegreeData(
              d.getCode(), d.getName(), plain(d.getTotalEcts()), subjectData.getOrDefault(d.getCode(), List.of())));
      subjectDtos.putIfAbsent(d.getCode(), List.of());
    }

    Map<String, SourceDto> sourceDtos = new HashMap<>();
    for (SourceDocument s : sources.findByDatasetVersionIdOrderByCode(versionId)) {
      sourceDtos.put(
          s.getCode(),
          new SourceDto(
              s.getCode(),
              s.getDocumentType().name(),
              s.getTitle(),
              s.getIssuer(),
              s.getPublicationDate(),
              s.getUrl(),
              s.getFileSha256(),
              s.getRetrievedAt()));
    }

    Map<String, TableData> tableData = new HashMap<>();
    for (RecognitionTable t : tables.findByDatasetVersionIdOrderByCode(versionId)) {
      tableData.put(
          PublishedDataset.tableKey(t.getCycle().getCode(), t.getDegree().getCode()),
          new TableData(
              t.getCode(),
              TableData.Status.valueOf(t.getStatus().name()),
              sourceRef(t.getSource()),
              plain(t.getTableMaxEcts()),
              t.getNotes()));
    }

    Map<String, List<RuleData>> ruleData = new HashMap<>();
    for (RecognitionRule r : rules.findWithDetailsByDatasetVersionIdOrderByCode(versionId)) {
      ruleData
          .computeIfAbsent(r.getRecognitionTable().getCode(), k -> new ArrayList<>())
          .add(
              new RuleData(
                  r.getCode(),
                  RuleData.TargetType.valueOf(r.getTargetType().name()),
                  r.getTargetSubject() == null ? null : r.getTargetSubject().getCode(),
                  plain(r.getRecognizedEcts()),
                  r.isConditional(),
                  r.getConditionText(),
                  sourceRef(r.getSource()),
                  r.getSourcePage(),
                  r.getSourceExcerpt(),
                  r.getRequirements().stream().map(q -> q.getModule().getCode()).collect(Collectors.toSet())));
    }

    return new PublishedDataset(
        info,
        cycleDtos,
        universityDtos,
        degreeDtos,
        subjectDtos.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))),
        degreeData,
        tableData,
        ruleData.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue()))),
        sourceDtos);
  }

  private static SourceRef sourceRef(SourceDocument source) {
    return source == null
        ? null
        : new SourceRef(
            source.getCode(),
            source.getDocumentType().name(),
            source.getTitle(),
            source.getPublicationDate(),
            source.getUrl());
  }
}
