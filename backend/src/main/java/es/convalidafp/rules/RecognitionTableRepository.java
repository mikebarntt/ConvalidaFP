package es.convalidafp.rules;

import es.convalidafp.catalog.VersionedRepository;
import java.util.List;
import java.util.Optional;

public interface RecognitionTableRepository extends VersionedRepository<RecognitionTable> {

  /** Tabla de un ciclo frente a un grado dentro de una versión. Vacío = sin datos (NO_DATA). */
  Optional<RecognitionTable> findByDatasetVersionIdAndCycleCodeAndDegreeCode(
      Long datasetVersionId, String cycleCode, String degreeCode);

  List<RecognitionTable> findByDatasetVersionIdAndCycleCodeOrderByCode(
      Long datasetVersionId, String cycleCode);
}
