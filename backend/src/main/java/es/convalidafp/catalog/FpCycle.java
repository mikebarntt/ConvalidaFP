package es.convalidafp.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Ciclo de FP de grado superior (fp_cycles.csv). Un mismo ciclo con dos normativas son dos filas. */
@Entity
@Table(name = "fp_cycle")
public class FpCycle extends VersionedEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "acronym")
  private String acronym;

  @Column(name = "professional_family")
  private String professionalFamily;

  @Column(name = "regulation_ref")
  private String regulationRef;

  @Column(name = "total_ects", precision = 6, scale = 2)
  private BigDecimal totalEcts;

  protected FpCycle() {}

  public FpCycle(
      DatasetVersion datasetVersion,
      String code,
      String name,
      String acronym,
      String professionalFamily,
      String regulationRef,
      BigDecimal totalEcts) {
    super(datasetVersion, code);
    this.name = name;
    this.acronym = acronym;
    this.professionalFamily = professionalFamily;
    this.regulationRef = regulationRef;
    this.totalEcts = totalEcts;
  }

  public String getName() {
    return name;
  }

  public String getAcronym() {
    return acronym;
  }

  public String getProfessionalFamily() {
    return professionalFamily;
  }

  public String getRegulationRef() {
    return regulationRef;
  }

  public BigDecimal getTotalEcts() {
    return totalEcts;
  }
}
