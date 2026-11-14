package es.convalidafp.rules;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.Degree;
import es.convalidafp.catalog.FpCycle;
import es.convalidafp.catalog.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabla de reconocimiento de un ciclo frente a un grado en un curso (recognition_tables.csv). */
@Entity
@Table(name = "recognition_table")
public class RecognitionTable extends VersionedEntity {

  /** VIGENTE = hay tabla firmada; SIN_CONVENIO = verificado que no la hay. */
  public enum Status {
    VIGENTE,
    SIN_CONVENIO
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private FpCycle cycle;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "degree_id", nullable = false)
  private Degree degree;

  @Column(name = "academic_year", nullable = false, length = 7)
  private String academicYear;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false)
  private Status status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id")
  private SourceDocument source;

  @Column(name = "table_max_ects", precision = 6, scale = 2)
  private BigDecimal tableMaxEcts;

  @Column(name = "notes", length = 2000)
  private String notes;

  protected RecognitionTable() {}

  public RecognitionTable(
      DatasetVersion datasetVersion,
      String code,
      FpCycle cycle,
      Degree degree,
      String academicYear,
      Status status,
      SourceDocument source,
      BigDecimal tableMaxEcts,
      String notes) {
    super(datasetVersion, code);
    this.cycle = cycle;
    this.degree = degree;
    this.academicYear = academicYear;
    this.status = status;
    this.source = source;
    this.tableMaxEcts = tableMaxEcts;
    this.notes = notes;
  }

  public FpCycle getCycle() {
    return cycle;
  }

  public Degree getDegree() {
    return degree;
  }

  public String getAcademicYear() {
    return academicYear;
  }

  public Status getStatus() {
    return status;
  }

  public SourceDocument getSource() {
    return source;
  }

  public BigDecimal getTableMaxEcts() {
    return tableMaxEcts;
  }

  public String getNotes() {
    return notes;
  }
}
