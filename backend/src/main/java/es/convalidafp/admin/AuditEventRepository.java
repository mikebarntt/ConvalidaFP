package es.convalidafp.admin;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Auditoría: solo permite añadir y leer. A propósito no extiende {@code JpaRepository}, para que no
 * exista ningún método de modificación ni de borrado.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

  AuditEvent save(AuditEvent event);

  /** Los hechos más recientes primero. */
  List<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);

  List<AuditEvent> findByActionOrderByIdDesc(AuditEvent.Action action);

  long count();
}
