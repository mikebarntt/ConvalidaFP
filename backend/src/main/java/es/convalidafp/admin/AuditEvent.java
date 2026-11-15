package es.convalidafp.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Un hecho de la auditoría: quién, qué acción, sobre qué versión y cuándo.
 *
 * <p>Solo se puede añadir. La entidad es inmutable, el repositorio no ofrece cambios ni borrados y
 * la base de datos los rechaza con un disparador.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

  /** Acciones que se auditan. */
  public enum Action {
    IMPORTAR,
    PUBLICAR,
    RESTAURAR,
    LOGIN_FALLIDO
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "actor", nullable = false, updatable = false, length = 100)
  private String actor;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "action", nullable = false, updatable = false)
  private Action action;

  @Column(name = "dataset_version_id", updatable = false)
  private Long datasetVersionId;

  @Column(name = "academic_year", updatable = false, length = 7)
  private String academicYear;

  @Column(name = "import_id", updatable = false)
  private Long importId;

  @Column(name = "detail", updatable = false, length = 500)
  private String detail;

  @Column(name = "ip", updatable = false, length = 45)
  private String ip;

  protected AuditEvent() {}

  AuditEvent(
      Instant occurredAt,
      String actor,
      Action action,
      Long datasetVersionId,
      String academicYear,
      Long importId,
      String detail,
      String ip) {
    this.occurredAt = occurredAt;
    this.actor = actor;
    this.action = action;
    this.datasetVersionId = datasetVersionId;
    this.academicYear = academicYear;
    this.importId = importId;
    this.detail = detail;
    this.ip = ip;
  }

  public Long getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getActor() {
    return actor;
  }

  public Action getAction() {
    return action;
  }

  public Long getDatasetVersionId() {
    return datasetVersionId;
  }

  public String getAcademicYear() {
    return academicYear;
  }

  public Long getImportId() {
    return importId;
  }

  public String getDetail() {
    return detail;
  }

  public String getIp() {
    return ip;
  }
}
