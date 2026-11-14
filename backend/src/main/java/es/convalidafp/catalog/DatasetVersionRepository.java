package es.convalidafp.catalog;

import es.convalidafp.catalog.DatasetVersion.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, Long> {

  /** La versión de un curso en un estado. Con PUBLICADO hay como mucho una. */
  Optional<DatasetVersion> findByAcademicYearAndStatus(String academicYear, Status status);

  /** La versión del curso más reciente en un estado: con PUBLICADO, el curso vigente. */
  Optional<DatasetVersion> findFirstByStatusOrderByAcademicYearDesc(Status status);

  /** Todas las versiones de un curso, de la más antigua a la más nueva. */
  List<DatasetVersion> findByAcademicYearOrderByIdAsc(String academicYear);
}
