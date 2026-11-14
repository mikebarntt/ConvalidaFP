package es.convalidafp.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Módulo de un ciclo de FP (fp_modules.csv). */
@Entity
@Table(name = "fp_module")
public class FpModule extends VersionedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cycle_id", nullable = false)
  private FpCycle cycle;

  @Column(name = "official_code")
  private String officialCode;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "ects", precision = 6, scale = 2)
  private BigDecimal ects;

  @Column(name = "course_year")
  private Integer courseYear;

  protected FpModule() {}

  public FpModule(
      DatasetVersion datasetVersion,
      String code,
      FpCycle cycle,
      String officialCode,
      String name,
      BigDecimal ects,
      Integer courseYear) {
    super(datasetVersion, code);
    this.cycle = cycle;
    this.officialCode = officialCode;
    this.name = name;
    this.ects = ects;
    this.courseYear = courseYear;
  }

  public FpCycle getCycle() {
    return cycle;
  }

  public String getOfficialCode() {
    return officialCode;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getEcts() {
    return ects;
  }

  public Integer getCourseYear() {
    return courseYear;
  }
}
