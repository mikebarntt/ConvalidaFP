package es.convalidafp.api;

import es.convalidafp.api.CatalogDtos.CycleDto;
import es.convalidafp.api.CatalogDtos.DatasetInfo;
import es.convalidafp.api.CatalogDtos.DegreeDto;
import es.convalidafp.api.CatalogDtos.SourceDto;
import es.convalidafp.api.CatalogDtos.SubjectDto;
import es.convalidafp.api.CatalogDtos.UniversityDto;
import es.convalidafp.simulation.DegreeData;
import es.convalidafp.simulation.RuleData;
import es.convalidafp.simulation.TableData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Una versión publicada del dataset, entera y en memoria, como objeto inmutable.
 *
 * <p>Cada petición obtiene una sola instancia y lee todo de ella. Por eso una simulación sale
 * siempre de una única versión, aunque se publique otra mientras se calcula (caso límite 14): la
 * publicación sustituye la instancia que recibirán las peticiones siguientes, no modifica esta.
 */
public record PublishedDataset(
    DatasetInfo info,
    List<CycleDto> cycles,
    List<UniversityDto> universities,
    List<DegreeDto> degrees,
    Map<String, List<SubjectDto>> subjectsByDegree,
    Map<String, DegreeData> degreeData,
    Map<String, TableData> tablesByCycleAndDegree,
    Map<String, List<RuleData>> rulesByTable,
    Map<String, SourceDto> sources,
    Set<String> degreesWithCompleteSubjects) {

  public PublishedDataset {
    cycles = List.copyOf(cycles);
    universities = List.copyOf(universities);
    degrees = List.copyOf(degrees);
    subjectsByDegree = Map.copyOf(subjectsByDegree);
    degreeData = Map.copyOf(degreeData);
    tablesByCycleAndDegree = Map.copyOf(tablesByCycleAndDegree);
    rulesByTable = Map.copyOf(rulesByTable);
    sources = Map.copyOf(sources);
    degreesWithCompleteSubjects = Set.copyOf(degreesWithCompleteSubjects);
  }

  static String tableKey(String cycleId, String degreeId) {
    return cycleId + "|" + degreeId;
  }

  /**
   * Valor de la cabecera ETag: cambia cada vez que se publica o se restaura una versión, y no cambia
   * mientras la versión publicada sea la misma.
   */
  public String etag() {
    return "\"dataset-" + info.versionId() + "-" + info.publishedAt().toEpochMilli() + "\"";
  }

  public Optional<CycleDto> cycle(String cycleId) {
    return cycles.stream().filter(c -> c.id().equals(cycleId)).findFirst();
  }

  public Optional<UniversityDto> university(String universityId) {
    return universities.stream().filter(u -> u.id().equals(universityId)).findFirst();
  }

  public Optional<DegreeDto> degree(String degreeId) {
    return degrees.stream().filter(d -> d.id().equals(degreeId)).findFirst();
  }

  /** La tabla de un ciclo frente a un grado; vacío = sin datos. */
  public Optional<TableData> table(String cycleId, String degreeId) {
    return Optional.ofNullable(tablesByCycleAndDegree.get(tableKey(cycleId, degreeId)));
  }

  /**
   * true si el dataset trae todas las asignaturas del grado; false si solo trae las que aparecen en
   * las tablas de reconocimiento (subjects_complete de degrees.csv).
   */
  public boolean subjectsComplete(String degreeId) {
    return degreesWithCompleteSubjects.contains(degreeId);
  }

  public List<RuleData> rules(String tableId) {
    return rulesByTable.getOrDefault(tableId, List.of());
  }

  // ---------- con error 404 si no existe ----------

  public CycleDto requireCycle(String cycleId) {
    return cycle(cycleId).orElseThrow(() -> notFound("el ciclo", cycleId));
  }

  public UniversityDto requireUniversity(String universityId) {
    return university(universityId).orElseThrow(() -> notFound("la universidad", universityId));
  }

  public DegreeDto requireDegree(String degreeId) {
    return degree(degreeId).orElseThrow(() -> notFound("el grado", degreeId));
  }

  public SourceDto requireSource(String sourceId) {
    SourceDto source = sources.get(sourceId);
    if (source == null) {
      throw notFound("el documento", sourceId);
    }
    return source;
  }

  private ResourceNotFoundException notFound(String what, String id) {
    return new ResourceNotFoundException(
        "No existe " + what + " «" + id + "» en los datos del curso " + info.academicYear() + ".");
  }
}
