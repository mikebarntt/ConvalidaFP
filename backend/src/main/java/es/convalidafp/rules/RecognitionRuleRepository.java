package es.convalidafp.rules;

import es.convalidafp.catalog.VersionedRepository;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;

public interface RecognitionRuleRepository extends VersionedRepository<RecognitionRule> {

  /**
   * Reglas de una tabla, ordenadas por identificador, con todo lo que necesita el motor ya cargado:
   * módulos exigidos, asignatura destino y fuente propia.
   */
  @EntityGraph(attributePaths = {"requirements", "requirements.module", "targetSubject", "source"})
  List<RecognitionRule> findByRecognitionTableIdOrderByCode(Long recognitionTableId);

  /** Todas las reglas de una versión con sus módulos, asignatura destino, fuente y tabla ya cargados. */
  @EntityGraph(
      attributePaths = {"requirements", "requirements.module", "targetSubject", "source", "recognitionTable"})
  List<RecognitionRule> findWithDetailsByDatasetVersionIdOrderByCode(Long datasetVersionId);
}
