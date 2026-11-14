package es.convalidafp.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Versión de un dataset: el contenido completo de un curso académico.
 *
 * <p>Todas las filas de datos pertenecen a una versión. Una lectura se fija a una única versión
 * resolviendo primero su {@code id} y filtrando después todo por él, de modo que publicar otra
 * versión mientras tanto no afecta al resultado. La base de datos garantiza que solo hay una
 * versión publicada por curso: para republicar un curso, la publicada anterior se archiva antes.
 */
@Entity
@Table(name = "dataset_version")
public class DatasetVersion {

  /** Estado de una versión. */
  public enum Status {
    BORRADOR,
    PUBLICADO,
    /** Fue la publicada de su curso y otra la sustituyó. Conserva sus datos y puede restaurarse. */
    ARCHIVADO
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "academic_year", nullable = false, updatable = false, length = 7)
  private String academicYear;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false)
  private Status status;

  @Column(name = "manifest_date")
  private LocalDate manifestDate;

  @Column(name = "reviewer")
  private String reviewer;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  protected DatasetVersion() {}

  private DatasetVersion(
      String academicYear, LocalDate manifestDate, String reviewer, Instant createdAt) {
    this.academicYear = academicYear;
    this.status = Status.BORRADOR;
    this.manifestDate = manifestDate;
    this.reviewer = reviewer;
    this.createdAt = createdAt;
  }

  /** Crea una versión en borrador para un curso con formato {@code AAAA-AA}. */
  public static DatasetVersion draft(
      String academicYear, LocalDate manifestDate, String reviewer, Instant now) {
    if (academicYear == null || !academicYear.matches("\\d{4}-\\d{2}")) {
      throw new IllegalArgumentException("El curso debe tener el formato AAAA-AA: " + academicYear);
    }
    return new DatasetVersion(academicYear, manifestDate, reviewer, now);
  }

  /**
   * Publica un borrador o restaura una versión archivada. Una versión ya publicada no vuelve a
   * publicarse. {@code publishedAt} guarda la fecha de la última publicación.
   *
   * <p>Para respetar la única versión publicada por curso, usar {@link DatasetPublicationService}.
   */
  public void publish(Instant now) {
    if (status == Status.PUBLICADO) {
      throw new IllegalStateException("La versión " + id + " ya está publicada");
    }
    this.status = Status.PUBLICADO;
    this.publishedAt = now;
    this.archivedAt = null;
  }

  /**
   * Archiva la versión publicada para que otra pueda ocupar su lugar. Debe guardarse antes de
   * publicar la nueva, porque la base de datos solo admite una versión publicada por curso.
   */
  public void archive(Instant now) {
    if (status != Status.PUBLICADO) {
      throw new IllegalStateException("Solo se puede archivar una versión publicada; la versión " + id + " está " + status);
    }
    this.status = Status.ARCHIVADO;
    this.archivedAt = now;
  }

  public boolean isPublished() {
    return status == Status.PUBLICADO;
  }

  public Long getId() {
    return id;
  }

  public String getAcademicYear() {
    return academicYear;
  }

  public Status getStatus() {
    return status;
  }

  public LocalDate getManifestDate() {
    return manifestDate;
  }

  public String getReviewer() {
    return reviewer;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
