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

/** Asignatura de un grado (degree_subjects.csv). */
@Entity
@Table(name = "degree_subject")
public class DegreeSubject extends VersionedEntity {

  /** Tipo de asignatura. */
  public enum SubjectType {
    BASICA,
    OBLIGATORIA,
    OPTATIVA,
    PRACTICAS_EXTERNAS,
    TFG
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "degree_id", nullable = false)
  private Degree degree;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "ects", precision = 6, scale = 2)
  private BigDecimal ects;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "subject_type", nullable = false)
  private SubjectType subjectType;

  @Column(name = "course_year")
  private Integer courseYear;

  @Column(name = "official_code")
  private String officialCode;

  protected DegreeSubject() {}

  public DegreeSubject(
      DatasetVersion datasetVersion,
      String code,
      Degree degree,
      String name,
      BigDecimal ects,
      SubjectType subjectType,
      Integer courseYear,
      String officialCode) {
    super(datasetVersion, code);
    this.degree = degree;
    this.name = name;
    this.ects = ects;
    this.subjectType = subjectType;
    this.courseYear = courseYear;
    this.officialCode = officialCode;
  }

  public Degree getDegree() {
    return degree;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getEcts() {
    return ects;
  }

  public SubjectType getSubjectType() {
    return subjectType;
  }

  public Integer getCourseYear() {
    return courseYear;
  }

  public String getOfficialCode() {
    return officialCode;
  }
}
