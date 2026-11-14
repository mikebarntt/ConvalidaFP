package es.convalidafp.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import es.convalidafp.catalog.DatasetPublishedEvent;
import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Caché en memoria (Caffeine) de las versiones publicadas.
 *
 * <p>Los datos cambian una vez al año, así que cada versión se lee de la base de datos una sola vez
 * y se sirve desde memoria. La caché se vacía al recibir un {@link DatasetPublishedEvent}, después
 * de que la publicación se haya confirmado en la base de datos.
 */
@Component
public class PublishedDatasets {

  private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\d{4}-\\d{2}");
  private static final String CURRENT = "";

  private final DatasetVersionRepository versions;
  private final PublishedDatasetLoader loader;

  /** Qué versión está publicada para cada curso ("" = el curso vigente). */
  private final Cache<String, Optional<Long>> publishedVersionByYear =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();

  /** El contenido completo de cada versión. */
  private final Cache<Long, PublishedDataset> datasetsByVersion = Caffeine.newBuilder().maximumSize(8).build();

  PublishedDatasets(DatasetVersionRepository versions, PublishedDatasetLoader loader) {
    this.versions = versions;
    this.loader = loader;
  }

  /**
   * La versión publicada de un curso, o la del curso vigente si {@code academicYear} es null.
   *
   * <p>Quien la pide debe usar la instancia devuelta para toda la petición: así todo sale de una
   * única versión aunque se publique otra mientras tanto.
   *
   * @throws InvalidRequestException si el curso no tiene el formato AAAA-AA
   * @throws ResourceNotFoundException si no hay ninguna versión publicada para ese curso
   */
  public PublishedDataset resolve(String academicYear) {
    if (academicYear != null && !ACADEMIC_YEAR.matcher(academicYear).matches()) {
      throw new InvalidRequestException("El curso debe tener el formato AAAA-AA, por ejemplo 2026-27.");
    }
    String key = academicYear == null ? CURRENT : academicYear;
    Long versionId =
        publishedVersionByYear
            .get(key, k -> lookUp(academicYear))
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        academicYear == null
                            ? "Todavía no hay ningún dataset publicado."
                            : "No hay datos publicados para el curso " + academicYear + "."));
    return datasetsByVersion.get(versionId, loader::load);
  }

  /** Cursos con una versión publicada, del más reciente al más antiguo. */
  public List<String> availableYears() {
    return versions.findByStatusOrderByAcademicYearDesc(Status.PUBLICADO).stream()
        .map(DatasetVersion::getAcademicYear)
        .toList();
  }

  private Optional<Long> lookUp(String academicYear) {
    Optional<DatasetVersion> version =
        academicYear == null
            ? versions.findFirstByStatusOrderByAcademicYearDesc(Status.PUBLICADO)
            : versions.findByAcademicYearAndStatus(academicYear, Status.PUBLICADO);
    return version.map(DatasetVersion::getId);
  }

  /**
   * Vacía la caché cuando se publica o se restaura una versión. Se ejecuta tras confirmarse la
   * transacción (o de inmediato si no hay ninguna): nunca se vuelve a cargar un dato sin confirmar.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDatasetPublished(DatasetPublishedEvent event) {
    publishedVersionByYear.invalidateAll();
    datasetsByVersion.invalidateAll();
  }
}
