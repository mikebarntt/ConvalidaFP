package es.convalidafp.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * Base de toda fila de datos de un dataset.
 *
 * <p>{@code id} es la clave técnica. {@code code} es el identificador del contrato de datos (por
 * ejemplo {@code UEJ-GP} o {@code R-001}): único dentro de una versión y estable entre cursos.
 */
@MappedSuperclass
public abstract class VersionedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dataset_version_id", nullable = false, updatable = false)
  private DatasetVersion datasetVersion;

  @Column(name = "code", nullable = false, updatable = false, length = 64)
  private String code;

  protected VersionedEntity() {}

  protected VersionedEntity(DatasetVersion datasetVersion, String code) {
    this.datasetVersion = datasetVersion;
    this.code = code;
  }

  public Long getId() {
    return id;
  }

  public DatasetVersion getDatasetVersion() {
    return datasetVersion;
  }

  public String getCode() {
    return code;
  }
}
