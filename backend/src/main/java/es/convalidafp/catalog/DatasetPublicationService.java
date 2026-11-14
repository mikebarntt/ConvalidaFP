package es.convalidafp.catalog;

import es.convalidafp.catalog.DatasetVersion.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica versiones de dataset manteniendo una sola versión publicada por curso.
 *
 * <p>Sirve igual para publicar un borrador que para restaurar una versión archivada: en ambos
 * casos, la versión que estuviera publicada en ese curso pasa a archivada.
 */
@Service
public class DatasetPublicationService {

  private final DatasetVersionRepository versions;
  private final Clock clock;

  public DatasetPublicationService(DatasetVersionRepository versions, Clock clock) {
    this.versions = versions;
    this.clock = clock;
  }

  /**
   * Publica la versión indicada (un borrador o una archivada) como la vigente de su curso.
   *
   * <p>Todo ocurre en una transacción: si la publicación falla, el archivado de la versión anterior
   * se deshace y esta sigue publicada.
   *
   * @throws IllegalArgumentException si la versión no existe
   * @throws IllegalStateException si la versión ya está publicada
   */
  @Transactional
  public DatasetVersion publish(Long versionId) {
    DatasetVersion target =
        versions
            .findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("No existe la versión " + versionId));
    if (target.isPublished()) {
      throw new IllegalStateException("La versión " + versionId + " ya está publicada");
    }
    Instant now = clock.instant();

    // La base de datos solo admite una versión publicada por curso: la anterior se archiva y se
    // escribe antes de publicar la nueva.
    Optional<DatasetVersion> current =
        versions.findByAcademicYearAndStatus(target.getAcademicYear(), Status.PUBLICADO);
    if (current.isPresent()) {
      current.get().archive(now);
      versions.saveAndFlush(current.get());
    }

    target.publish(now);
    return versions.saveAndFlush(target);
  }
}
