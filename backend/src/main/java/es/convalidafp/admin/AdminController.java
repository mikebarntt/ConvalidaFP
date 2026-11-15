package es.convalidafp.admin;

import es.convalidafp.admin.AuditLog.Actor;
import es.convalidafp.api.InvalidRequestException;
import es.convalidafp.catalog.DatasetVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * API de administración. Todo exige una sesión con rol ADMIN salvo pedir el token CSRF. El login
 * ({@code POST /api/v1/admin/login}) y el logout los atiende Spring Security.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(
    name = "Administración",
    description =
        "Requiere iniciar sesión como administrador. Login: POST /api/v1/admin/login con los campos de"
            + " formulario `username` y `password` y la cabecera X-XSRF-TOKEN. Las peticiones POST necesitan"
            + " esa misma cabecera.")
class AdminController {

  private final AdminDatasetService datasets;
  private final AuditLog audit;
  private final ObjectMapper json;

  AdminController(AdminDatasetService datasets, AuditLog audit, ObjectMapper json) {
    this.datasets = datasets;
    this.audit = audit;
    this.json = json;
  }

  // ---------- sesión ----------

  @GetMapping("/csrf")
  @Operation(
      summary = "Token CSRF",
      description = "No requiere sesión. Deja el token en la cookie XSRF-TOKEN y lo devuelve en el cuerpo.")
  Map<String, String> csrf(@Parameter(hidden = true) CsrfToken token) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("headerName", token.getHeaderName());
    body.put("parameterName", token.getParameterName());
    body.put("token", token.getToken());
    return body;
  }

  @GetMapping("/session")
  @Operation(summary = "Administrador con la sesión iniciada")
  Map<String, String> session(@Parameter(hidden = true) Principal principal) {
    return Map.of("username", principal.getName());
  }

  // ---------- importaciones ----------

  @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Subir un ZIP con un dataset",
      description =
          "Devuelve el informe de validación y las diferencias con lo publicado. Si no hay errores, el"
              + " dataset queda en borrador; si los hay, no se guarda ningún dato.")
  ResponseEntity<ImportDto> upload(
      @RequestParam("file") MultipartFile file,
      @Parameter(hidden = true) Principal principal,
      @Parameter(hidden = true) HttpServletRequest request) {
    if (file.isEmpty()) {
      throw new InvalidRequestException("El fichero está vacío.");
    }
    ImportRecord record;
    try (InputStream zip = file.getInputStream()) {
      record = datasets.importZip(file.getOriginalFilename(), zip, actor(principal, request));
    } catch (IOException e) {
      throw new InvalidRequestException("No se puede leer el fichero subido.");
    }
    return ResponseEntity.created(URI.create("/api/v1/admin/imports/" + record.getId())).body(toDto(record, true));
  }

  @GetMapping("/imports")
  @Operation(summary = "Importaciones, de la más reciente a la más antigua (sin el informe)")
  List<ImportDto> imports() {
    return datasets.listImports().stream().map(r -> toDto(r, false)).toList();
  }

  @GetMapping("/imports/{id}")
  @Operation(summary = "Estado e informe de una importación")
  ImportDto importById(@PathVariable Long id) {
    return toDto(datasets.getImport(id), true);
  }

  @PostMapping("/imports/{id}/publish")
  @Operation(summary = "Publicar el borrador de una importación como nueva versión")
  ImportDto publish(
      @PathVariable Long id,
      @Parameter(hidden = true) Principal principal,
      @Parameter(hidden = true) HttpServletRequest request) {
    return toDto(datasets.publishImport(id, actor(principal, request)), true);
  }

  // ---------- versiones ----------

  @GetMapping("/versions")
  @Operation(summary = "Versiones de dataset, de la más reciente a la más antigua")
  List<VersionDto> versions() {
    return datasets.listVersions().stream().map(AdminController::toDto).toList();
  }

  @PostMapping("/versions/{id}/restore")
  @Operation(
      summary = "Restaurar una versión archivada",
      description = "Vuelve a ser la versión publicada de su curso; la que lo era pasa a archivada.")
  VersionDto restore(
      @PathVariable Long id,
      @Parameter(hidden = true) Principal principal,
      @Parameter(hidden = true) HttpServletRequest request) {
    return toDto(datasets.restore(id, actor(principal, request)));
  }

  // ---------- auditoría ----------

  @GetMapping("/audit")
  @Operation(summary = "Registro de quién importó, publicó o restauró qué versión y cuándo")
  List<AuditDto> audit(
      @Parameter(description = "Número máximo de entradas (por defecto 100, máximo 500)")
          @RequestParam(defaultValue = "100")
          int limit) {
    return audit.latest(limit).stream()
        .map(
            e ->
                new AuditDto(
                    e.getId(),
                    e.getOccurredAt(),
                    e.getActor(),
                    e.getAction().name(),
                    e.getDatasetVersionId(),
                    e.getAcademicYear(),
                    e.getImportId(),
                    e.getDetail(),
                    e.getIp()))
        .toList();
  }

  // ---------- respuestas ----------

  /**
   * Una importación.
   *
   * @param outcome ACEPTADA (quedó en borrador) o RECHAZADA (tenía errores y no se guardó nada)
   * @param versionStatus estado actual de su versión: BORRADOR, PUBLICADO o ARCHIVADO; null si se
   *     rechazó o si el borrador se descartó
   * @param report errores, avisos y diferencias con lo publicado; null en el listado
   */
  record ImportDto(
      Long id,
      Instant createdAt,
      String createdBy,
      String fileName,
      String academicYear,
      String outcome,
      Long datasetVersionId,
      String versionStatus,
      int errorCount,
      int warningCount,
      JsonNode report) {}

  record VersionDto(
      Long id,
      String academicYear,
      String status,
      LocalDate manifestDate,
      String reviewer,
      Instant createdAt,
      Instant publishedAt,
      Instant archivedAt) {}

  record AuditDto(
      Long id,
      Instant occurredAt,
      String actor,
      String action,
      Long datasetVersionId,
      String academicYear,
      Long importId,
      String detail,
      String ip) {}

  private ImportDto toDto(ImportRecord record, boolean withReport) {
    return new ImportDto(
        record.getId(),
        record.getCreatedAt(),
        record.getCreatedBy(),
        record.getFileName(),
        record.getAcademicYear(),
        record.getOutcome().name(),
        record.getDatasetVersionId(),
        datasets.versionStatus(record).map(Enum::name).orElse(null),
        record.getErrorCount(),
        record.getWarningCount(),
        withReport ? json.readTree(record.getReportJson()) : null);
  }

  private static VersionDto toDto(DatasetVersion version) {
    return new VersionDto(
        version.getId(),
        version.getAcademicYear(),
        version.getStatus().name(),
        version.getManifestDate(),
        version.getReviewer(),
        version.getCreatedAt(),
        version.getPublishedAt(),
        version.getArchivedAt());
  }

  private static Actor actor(Principal principal, HttpServletRequest request) {
    return new Actor(principal.getName(), request.getRemoteAddr());
  }
}
