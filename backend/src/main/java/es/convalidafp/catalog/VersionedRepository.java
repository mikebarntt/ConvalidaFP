package es.convalidafp.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Consultas comunes a todos los datos de un dataset. Todas reciben el {@code id} de la versión: así
 * una lectura queda fijada a una única versión.
 */
@NoRepositoryBean
public interface VersionedRepository<T extends VersionedEntity> extends JpaRepository<T, Long> {

  List<T> findByDatasetVersionIdOrderByCode(Long datasetVersionId);

  Optional<T> findByDatasetVersionIdAndCode(Long datasetVersionId, String code);

  long countByDatasetVersionId(Long datasetVersionId);
}
