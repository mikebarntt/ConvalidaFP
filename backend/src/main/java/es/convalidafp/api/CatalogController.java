package es.convalidafp.api;

import es.convalidafp.api.CatalogDtos.CycleDto;
import es.convalidafp.api.CatalogDtos.DatasetResponse;
import es.convalidafp.api.CatalogDtos.DegreeDto;
import es.convalidafp.api.CatalogDtos.SourceDto;
import es.convalidafp.api.CatalogDtos.SubjectDto;
import es.convalidafp.api.CatalogDtos.UniversityDto;
import es.convalidafp.simulation.TableData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** Catálogo de solo lectura: ciclos, universidades, grados, asignaturas, documentos y versión. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catálogo", description = "Ciclos, universidades, grados, asignaturas y documentos oficiales")
class CatalogController {

  static final String YEAR_DESCRIPTION =
      "Curso académico con formato AAAA-AA. Si se omite, se usa el curso vigente publicado.";

  private final PublishedDatasets datasets;
  private final HttpCaching caching;

  CatalogController(PublishedDatasets datasets, HttpCaching caching) {
    this.datasets = datasets;
    this.caching = caching;
  }

  @GetMapping("/dataset")
  @Operation(summary = "Versión publicada, curso y fecha de actualización")
  ResponseEntity<DatasetResponse> dataset(
      @Parameter(description = YEAR_DESCRIPTION) @RequestParam(required = false) String year, WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    DatasetResponse body =
        new DatasetResponse(
            dataset.info().versionId(),
            dataset.info().academicYear(),
            dataset.info().publishedAt(),
            datasets.availableYears());
    return caching.respond(request, dataset, body);
  }

  @GetMapping("/cycles")
  @Operation(summary = "Ciclos de FP disponibles")
  ResponseEntity<List<CycleDto>> cycles(
      @Parameter(description = YEAR_DESCRIPTION) @RequestParam(required = false) String year, WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    return caching.respond(request, dataset, dataset.cycles());
  }

  @GetMapping("/universities")
  @Operation(summary = "Universidades")
  ResponseEntity<List<UniversityDto>> universities(
      @Parameter(description = YEAR_DESCRIPTION) @RequestParam(required = false) String year, WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    return caching.respond(request, dataset, dataset.universities());
  }

  @GetMapping("/degrees")
  @Operation(
      summary = "Grados",
      description =
          "Con `cycle`, solo los grados que tienen fila en las tablas de reconocimiento para ese ciclo;"
              + " `tableStatus` indica si hay tabla vigente o si se ha verificado que no hay convenio.")
  ResponseEntity<List<DegreeDto>> degrees(
      @Parameter(description = "Identificador de universidad") @RequestParam(required = false) String university,
      @Parameter(description = "Identificador de ciclo") @RequestParam(required = false) String cycle,
      @Parameter(description = YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    if (university != null) {
      dataset.requireUniversity(university);
    }
    if (cycle != null) {
      dataset.requireCycle(cycle);
    }
    List<DegreeDto> body =
        dataset.degrees().stream()
            .filter(d -> university == null || d.universityId().equals(university))
            .map(
                d -> {
                  if (cycle == null) {
                    return Optional.of(d);
                  }
                  Optional<TableData> table = dataset.table(cycle, d.id());
                  return table.map(t -> d.withTableStatus(t.status().name()));
                })
            .flatMap(Optional::stream)
            .toList();
    return caching.respond(request, dataset, body);
  }

  @GetMapping("/degrees/{id}/subjects")
  @Operation(summary = "Asignaturas de un grado")
  ResponseEntity<List<SubjectDto>> subjects(
      @PathVariable String id,
      @Parameter(description = YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    dataset.requireDegree(id);
    return caching.respond(request, dataset, dataset.subjectsByDegree().get(id));
  }

  @GetMapping("/sources/{id}")
  @Operation(summary = "Documento oficial del que sale una tabla o una regla")
  ResponseEntity<SourceDto> source(
      @PathVariable String id,
      @Parameter(description = YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    return caching.respond(request, dataset, dataset.requireSource(id));
  }
}
