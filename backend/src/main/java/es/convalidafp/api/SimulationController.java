package es.convalidafp.api;

import es.convalidafp.api.SimulationDtos.ComparisonResponse;
import es.convalidafp.api.SimulationDtos.ExploreEntryDto;
import es.convalidafp.api.SimulationDtos.ExploreResponse;
import es.convalidafp.api.SimulationDtos.SimulationResponse;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.simulation.TableData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Simulación, comparación y exploración. Son GET para que las respuestas se puedan cachear y
 * compartir por URL. Cada petición resuelve una única versión publicada y calcula todo con ella.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Simulación", description = "Qué se reconoce de un ciclo de FP en un grado universitario")
class SimulationController {

  static final int MAX_COMPARED_DEGREES = 4;

  private final PublishedDatasets datasets;
  private final SimulationService simulations;
  private final HttpCaching caching;

  SimulationController(PublishedDatasets datasets, SimulationService simulations, HttpCaching caching) {
    this.datasets = datasets;
    this.simulations = simulations;
    this.caching = caching;
  }

  @GetMapping("/simulations")
  @Operation(
      summary = "Simulación de un ciclo frente a un grado",
      description =
          "Asignaturas reconocidas, ECTS, tope aplicado, asignaturas restantes y fuente de cada"
              + " reconocimiento. Asume que el estudiante tiene el título del ciclo. El resultado es"
              + " orientativo, no vinculante.")
  ResponseEntity<SimulationResponse> simulate(
      @Parameter(description = "Identificador de ciclo") @RequestParam String cycle,
      @Parameter(description = "Identificador de grado") @RequestParam String degree,
      @Parameter(description = CatalogController.YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    SimulationResponse body = SimulationDtos.toResponse(dataset, simulations.simulate(dataset, cycle, degree));
    return caching.respond(request, dataset, body);
  }

  // Sin «produces»: así los errores siguen saliendo en Problem Details (JSON).
  @GetMapping("/simulations/report.pdf")
  @Operation(
      summary = "Informe PDF de una simulación",
      description =
          "El mismo desglose que la simulación: ECTS, tope, asignaturas por curso con su fuente,"
              + " condicionados aparte, asignaturas restantes y el aviso de que es orientativo.")
  ResponseEntity<byte[]> report(
      @Parameter(description = "Identificador de ciclo") @RequestParam String cycle,
      @Parameter(description = "Identificador de grado") @RequestParam String degree,
      @Parameter(description = CatalogController.YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    SimulationResponse simulation = SimulationDtos.toResponse(dataset, simulations.simulate(dataset, cycle, degree));
    ResponseEntity<byte[]> response = caching.respond(request, dataset, SimulationReportPdf.render(simulation));
    String filename = "convalidafp-" + simulation.cycle().id() + "-" + simulation.degree().id() + ".pdf";
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(response.getBody());
  }

  @GetMapping("/comparisons")
  @Operation(summary = "Comparación de un ciclo frente a un máximo de 4 grados")
  ResponseEntity<ComparisonResponse> compare(
      @Parameter(description = "Identificador de ciclo") @RequestParam String cycle,
      @Parameter(description = "Entre 1 y 4 identificadores de grado, separados por comas") @RequestParam
          String degrees,
      @Parameter(description = CatalogController.YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    List<String> degreeIds =
        Arrays.stream(degrees.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    if (degreeIds.isEmpty() || degreeIds.size() > MAX_COMPARED_DEGREES) {
      throw new InvalidRequestException(
          "Indica entre 1 y " + MAX_COMPARED_DEGREES + " grados distintos para comparar.");
    }
    PublishedDataset dataset = datasets.resolve(year);
    List<SimulationResponse> results =
        degreeIds.stream()
            .map(degreeId -> SimulationDtos.toResponse(dataset, simulations.simulate(dataset, cycle, degreeId)))
            .toList();
    ComparisonResponse body = new ComparisonResponse(dataset.info(), dataset.requireCycle(cycle), results);
    return caching.respond(request, dataset, body);
  }

  @GetMapping("/explore")
  @Operation(summary = "Grados que reconocen algo desde un ciclo, ordenados de más a menos ECTS")
  ResponseEntity<ExploreResponse> explore(
      @Parameter(description = "Identificador de ciclo") @RequestParam String cycle,
      @Parameter(description = CatalogController.YEAR_DESCRIPTION) @RequestParam(required = false) String year,
      WebRequest request) {
    PublishedDataset dataset = datasets.resolve(year);
    dataset.requireCycle(cycle);
    List<ExploreEntryDto> results =
        dataset.degrees().stream()
            .filter(d -> dataset.table(cycle, d.id()).map(t -> t.status() == TableData.Status.VIGENTE).orElse(false))
            .map(d -> simulations.simulate(dataset, cycle, d.id()))
            .filter(Recognized.class::isInstance)
            .map(Recognized.class::cast)
            .filter(r -> r.recognizedEcts().signum() > 0)
            .map(r -> SimulationDtos.toExploreEntry(dataset, r))
            .sorted(
                Comparator.comparing(ExploreEntryDto::recognizedEcts)
                    .reversed()
                    .thenComparing(e -> e.degree().id()))
            .toList();
    ExploreResponse body =
        new ExploreResponse(dataset.info(), dataset.requireCycle(cycle), SimulationDtos.NOTICE, results);
    return caching.respond(request, dataset, body);
  }
}
