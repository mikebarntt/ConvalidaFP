package es.convalidafp.admin;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRecordRepository extends JpaRepository<ImportRecord, Long> {

  /** Las importaciones más recientes primero. */
  List<ImportRecord> findAllByOrderByIdDesc();
}
