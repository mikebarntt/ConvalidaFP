package es.convalidafp.admin;

import es.convalidafp.admin.AuditEvent.Action;
import es.convalidafp.admin.AuditLog.Actor;
import es.convalidafp.admin.ImportRecord.Outcome;
import es.convalidafp.api.ConflictException;
import es.convalidafp.api.InvalidRequestException;
import es.convalidafp.api.ResourceNotFoundException;
import es.convalidafp.catalog.DatasetPublicationService;
import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.ingestion.DatasetFiles;
import es.convalidafp.ingestion.DatasetImportService;
import es.convalidafp.ingestion.ImportResult;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Operaciones del panel de administración sobre datasets: importar, publicar y restaurar.
 *
 * <p>Cada operación ocurre en una sola transacción junto con su entrada de auditoría: o quedan las
 * dos cosas, o ninguna.
 */
@Service
public class AdminDatasetService {

  private final DatasetImportService importer;
  private final DatasetPublicationService publication;
  private final DatasetVersionRepository versions;
  private final ImportRecordRepository imports;
  private final AuditLog audit;
  private final ObjectMapper json;
  private final Clock clock;

  AdminDatasetService(
      DatasetImportService importer,
      DatasetPublicationService publication,
      DatasetVersionRepository versions,
      ImportRecordRepository imports,
      AuditLog audit,
      ObjectMapper json,
      Clock clock) {
    this.importer = importer;
    this.publication = publication;
    this.versions = versions;
    this.imports = imports;
    this.audit = audit;
    this.json = json;
    this.clock = clock;
  }

  /**
   * Importa un dataset en ZIP. Se acepte o no, queda guardada una importación con su informe.
   *
   * @throws InvalidRequestException si el ZIP no se puede leer
   */
  @Transactional
  public ImportRecord importZip(String fileName, InputStream zip, Actor actor) {
    DatasetFiles files;
    try {
      files = DatasetFiles.fromZip(zip);
    } catch (IOException | IllegalArgumentException e) {
      throw new InvalidRequestException("No se puede leer el ZIP: " + e.getMessage());
    }
    ImportResult result = importer.importDataset(files);

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("errors", result.report().errors());
    report.put("warnings", result.report().warnings());
    report.put("diff", result.diff());
    ImportRecord record =
        imports.save(
            new ImportRecord(
                clock.instant(),
                actor.username(),
                fileName,
                result.academicYear(),
                result.accepted() ? Outcome.ACEPTADA : Outcome.RECHAZADA,
                result.datasetVersionId(),
                result.report().errors().size(),
                result.report().warnings().size(),
                json.writeValueAsString(report)));

    DatasetVersion draft = result.accepted() ? versions.findById(result.datasetVersionId()).orElseThrow() : null;
    audit.record(
        actor,
        Action.IMPORTAR,
        draft,
        record.getId(),
        result.accepted()
            ? "Importación aceptada como borrador (" + record.getWarningCount() + " avisos)"
            : "Importación rechazada (" + record.getErrorCount() + " errores)");
    return record;
  }

  /**
   * Publica el borrador que creó una importación.
   *
   * @throws ResourceNotFoundException si la importación no existe
   * @throws ConflictException si no hay borrador que publicar
   */
  @Transactional
  public ImportRecord publishImport(Long importId, Actor actor) {
    ImportRecord record = getImport(importId);
    if (record.getOutcome() == Outcome.RECHAZADA) {
      throw new ConflictException("La importación " + importId + " fue rechazada: no hay nada que publicar.");
    }
    DatasetVersion version =
        Optional.ofNullable(record.getDatasetVersionId())
            .flatMap(versions::findById)
            .orElseThrow(() -> new ConflictException("El borrador de la importación " + importId + " ya no existe."));
    if (version.getStatus() == Status.PUBLICADO) {
      throw new ConflictException("La versión de la importación " + importId + " ya está publicada.");
    }
    if (version.getStatus() == Status.ARCHIVADO) {
      throw new ConflictException(
          "La versión de la importación " + importId + " está archivada. Para volver a publicarla, restáurala.");
    }
    DatasetVersion published = publication.publish(version.getId());
    audit.record(actor, Action.PUBLICAR, published, importId, "Publicada como versión vigente del curso");
    return record;
  }

  /**
   * Restaura una versión archivada: vuelve a ser la publicada de su curso y la que lo era pasa a
   * archivada.
   *
   * @throws ResourceNotFoundException si la versión no existe
   * @throws ConflictException si la versión no está archivada
   */
  @Transactional
  public DatasetVersion restore(Long versionId, Actor actor) {
    DatasetVersion version =
        versions
            .findById(versionId)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la versión " + versionId + "."));
    if (version.getStatus() != Status.ARCHIVADO) {
      throw new ConflictException(
          "Solo se puede restaurar una versión archivada; la versión " + versionId + " está en estado "
              + version.getStatus() + ".");
    }
    DatasetVersion restored = publication.publish(versionId);
    audit.record(actor, Action.RESTAURAR, restored, null, "Restaurada como versión vigente del curso");
    return restored;
  }

  @Transactional(readOnly = true)
  public ImportRecord getImport(Long importId) {
    return imports
        .findById(importId)
        .orElseThrow(() -> new ResourceNotFoundException("No existe la importación " + importId + "."));
  }

  @Transactional(readOnly = true)
  public List<ImportRecord> listImports() {
    return imports.findAllByOrderByIdDesc();
  }

  /** El estado actual de la versión de una importación; vacío si se rechazó o se descartó. */
  @Transactional(readOnly = true)
  public Optional<Status> versionStatus(ImportRecord record) {
    return Optional.ofNullable(record.getDatasetVersionId()).flatMap(versions::findById).map(DatasetVersion::getStatus);
  }

  /** Todas las versiones, de la más reciente a la más antigua. */
  @Transactional(readOnly = true)
  public List<DatasetVersion> listVersions() {
    return versions.findAll().stream()
        .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
        .toList();
  }
}
