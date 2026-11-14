package es.convalidafp.catalog;

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

/** Grado universitario (degrees.csv). Un grado en dos centros con tablas distintas son dos filas. */
@Entity
@Table(name = "degree")
public class Degree extends VersionedEntity {

  /** Grado simple o doble grado. */
  public enum DegreeType {
    SIMPLE,
    DOBLE
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "university_id", nullable = false)
  private University university;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "degree_type", nullable = false)
  private DegreeType degreeType;

  @Column(name = "center")
  private String center;

  @Column(name = "total_ects", precision = 6, scale = 2)
  private BigDecimal totalEcts;

  @Column(name = "plan_code")
  private String planCode;

  @Column(name = "url", length = 1000)
  private String url;

  protected Degree() {}

  public Degree(
      DatasetVersion datasetVersion,
      String code,
      University university,
      String name,
      DegreeType degreeType,
      String center,
      BigDecimal totalEcts,
      String planCode,
      String url) {
    super(datasetVersion, code);
    this.university = university;
    this.name = name;
    this.degreeType = degreeType;
    this.center = center;
    this.totalEcts = totalEcts;
    this.planCode = planCode;
    this.url = url;
  }

  public University getUniversity() {
    return university;
  }

  public String getName() {
    return name;
  }

  public DegreeType getDegreeType() {
    return degreeType;
  }

  public String getCenter() {
    return center;
  }

  public BigDecimal getTotalEcts() {
    return totalEcts;
  }

  public String getPlanCode() {
    return planCode;
  }

  public String getUrl() {
    return url;
  }
}
