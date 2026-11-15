package es.convalidafp.admin;

import es.convalidafp.admin.AuditEvent.Action;
import es.convalidafp.catalog.DatasetVersion;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de auditoría. Se escribe dentro de la misma transacción que la acción auditada: si la
 * acción falla y se deshace, su entrada de auditoría también.
 */
@Service
public class AuditLog {

  static final int MAX_PAGE_SIZE = 500;
  private static final int MAX_ACTOR = 100;
  private static final int MAX_DETAIL = 500;

  private final AuditEventRepository events;
  private final Clock clock;

  AuditLog(AuditEventRepository events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  /** Una acción de un administrador sobre una versión de dataset. */
  @Transactional
  public AuditEvent record(Actor actor, Action action, DatasetVersion version, Long importId, String detail) {
    return events.save(
        new AuditEvent(
            clock.instant(),
            truncate(actor.username(), MAX_ACTOR),
            action,
            version == null ? null : version.getId(),
            version == null ? null : version.getAcademicYear(),
            importId,
            truncate(detail, MAX_DETAIL),
            actor.ip()));
  }

  /** Un intento de login fallido. El actor es el nombre de usuario que se intentó. */
  @Transactional
  public AuditEvent recordFailedLogin(String attemptedUsername, String ip, String detail) {
    String actor = attemptedUsername == null || attemptedUsername.isBlank() ? "(sin usuario)" : attemptedUsername;
    return events.save(
        new AuditEvent(
            clock.instant(),
            truncate(actor, MAX_ACTOR),
            Action.LOGIN_FALLIDO,
            null,
            null,
            null,
            truncate(detail, MAX_DETAIL),
            ip));
  }

  /** Los hechos más recientes primero, como mucho {@value #MAX_PAGE_SIZE}. */
  @Transactional(readOnly = true)
  public List<AuditEvent> latest(int limit) {
    return events.findAllByOrderByIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE_SIZE))));
  }

  private static String truncate(String text, int max) {
    return text == null || text.length() <= max ? text : text.substring(0, max);
  }

  /** Quién hace una acción y desde qué IP. */
  public record Actor(String username, String ip) {}
}
