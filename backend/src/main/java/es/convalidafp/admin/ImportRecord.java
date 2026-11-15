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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una importación: un dataset subido por un administrador, se haya aceptado o no, con su informe
 * completo (errores, avisos y diferencias con lo publicado) guardado en JSON.
 */
@Entity
@Table(name = "dataset_import")
public class ImportRecord {

  /** ACEPTADA = quedó guardada como borrador; RECHAZADA = tenía errores y no se guardó nada. */
  public enum Outcome {
    ACEPTADA,
    RECHAZADA
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  private String createdBy;

  @Column(name = "file_name", updatable = false)
  private String fileName;

  @Column(name = "academic_year", updatable = false, length = 7)
  private String academicYear;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "outcome", nullable = false, updatable = false)
  private Outcome outcome;

  /** La base de datos lo pone a NULL si el borrador se descarta; la aplicación nunca lo cambia. */
  @Column(name = "dataset_version_id", updatable = false)
  private Long datasetVersionId;

  @Column(name = "error_count", nullable = false, updatable = false)
  private int errorCount;

  @Column(name = "warning_count", nullable = false, updatable = false)
  private int warningCount;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(name = "report_json", nullable = false, updatable = false)
  private String reportJson;

  protected ImportRecord() {}

  ImportRecord(
      Instant createdAt,
      String createdBy,
      String fileName,
      String academicYear,
      Outcome outcome,
      Long datasetVersionId,
      int errorCount,
      int warningCount,
      String reportJson) {
    this.createdAt = createdAt;
    this.createdBy = createdBy;
    this.fileName = fileName;
    this.academicYear = academicYear;
    this.outcome = outcome;
    this.datasetVersionId = datasetVersionId;
    this.errorCount = errorCount;
    this.warningCount = warningCount;
    this.reportJson = reportJson;
  }

  public Long getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public String getFileName() {
    return fileName;
  }

  public String getAcademicYear() {
    return academicYear;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public Long getDatasetVersionId() {
    return datasetVersionId;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public int getWarningCount() {
    return warningCount;
  }

  public String getReportJson() {
    return reportJson;
  }
}
