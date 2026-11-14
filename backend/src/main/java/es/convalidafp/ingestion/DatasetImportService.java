package es.convalidafp.ingestion;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.ingestion.DatasetValidator.ValidatedDataset;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Importa datasets: los valida, los guarda como borrador y calcula las diferencias con lo
 * publicado. Publicar el borrador es un paso aparte ({@code DatasetPublicationService}).
 */
@Service
public class DatasetImportService {

  /** Tablas con datos de un dataset, en el orden en que hay que vaciarlas (primero las que dependen). */
  private static final List<String> ENTITIES_IN_DELETE_ORDER =
      List.of(
          "RuleRequirement",
          "RecognitionRule",
          "RecognitionTable",
          "SourceDocument",
          "DegreeSubject",
          "Degree",
          "FpModule",
          "FpCycle",
          "University");

  private final DatasetValidator validator;
  private final DatasetPersister persister;
  private final DatasetSnapshotReader snapshots;
  private final DatasetVersionRepository versions;
  private final EntityManager em;
  private final Clock clock;

  DatasetImportService(
      DatasetValidator validator,
      DatasetPersister persister,
      DatasetSnapshotReader snapshots,
      DatasetVersionRepository versions,
      EntityManager em,
      Clock clock) {
    this.validator = validator;
    this.persister = persister;
    this.snapshots = snapshots;
    this.versions = versions;
    this.em = em;
    this.clock = clock;
  }

  /**
   * Valida un dataset y, si no tiene errores, lo guarda como versión en borrador.
   *
   * <p>Un solo error rechaza la carga entera: no se guarda nada. Los avisos no la bloquean.
   */
  @Transactional
  public ImportResult importDataset(DatasetFiles files) {
    ValidatedDataset validated = validator.validate(files);
    RawDataset data = validated.dataset();
    if (!validated.report().isValid()) {
      return new ImportResult(data.academicYear(), validated.report(), null, null);
    }
    DatasetVersion draft = persister.persistAsDraft(data, clock.instant());
    em.clear();

    Optional<DatasetVersion> baseline = baselineFor(data.academicYear());
    DatasetDiff diff =
        DatasetDiff.between(
            baseline.map(DatasetVersion::getId).orElse(null),
            baseline.map(DatasetVersion::getAcademicYear).orElse(null),
            baseline.map(v -> snapshots.read(v.getId())).orElseGet(DatasetSnapshot::empty),
            DatasetSnapshot.of(data));
    return new ImportResult(data.academicYear(), validated.report(), draft.getId(), diff);
  }

  /**
   * Versión con la que se compara un dataset nuevo: la publicada de su mismo curso y, si no hay, la
   * publicada del curso anterior más reciente (lo habitual en la actualización anual).
   */
  private Optional<DatasetVersion> baselineFor(String academicYear) {
    return versions
        .findByAcademicYearAndStatus(academicYear, Status.PUBLICADO)
        .or(
            () ->
                versions.findFirstByStatusAndAcademicYearLessThanOrderByAcademicYearDesc(
                    Status.PUBLICADO, academicYear));
  }

  /**
   * Borra un borrador con todos sus datos. Las versiones publicadas o archivadas no se borran.
   *
   * @throws IllegalArgumentException si la versión no existe
   * @throws IllegalStateException si la versión no es un borrador
   */
  @Transactional
  public void discardDraft(Long versionId) {
    DatasetVersion version =
        versions
            .findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("No existe la versión " + versionId));
    if (version.getStatus() != Status.BORRADOR) {
      throw new IllegalStateException(
          "Solo se puede descartar un borrador; la versión " + versionId + " está " + version.getStatus());
    }
    em.flush();
    for (String entity : ENTITIES_IN_DELETE_ORDER) {
      em.createQuery("delete from " + entity + " e where e.datasetVersion.id = :id")
          .setParameter("id", versionId)
          .executeUpdate();
    }
    em.createQuery("delete from DatasetVersion v where v.id = :id").setParameter("id", versionId).executeUpdate();
    em.clear();
  }
}
