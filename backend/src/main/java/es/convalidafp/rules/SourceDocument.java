package es.convalidafp.rules;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.VersionedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Documento oficial del que sale una tabla o una regla (source_documents.csv). */
@Entity
@Table(name = "source_document")
public class SourceDocument extends VersionedEntity {

  /** Tipo de documento. */
  public enum DocumentType {
    CONVENIO,
    ADENDA,
    TABLA_UNIVERSIDAD,
    BOLETIN_OFICIAL,
    OTRO
  }

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "document_type", nullable = false)
  private DocumentType documentType;

  @Column(name = "title", nullable = false, length = 500)
  private String title;

  @Column(name = "issuer")
  private String issuer;

  @Column(name = "publication_date")
  private LocalDate publicationDate;

  @Column(name = "url", length = 1000)
  private String url;

  @Column(name = "file_sha256", length = 64)
  private String fileSha256;

  @Column(name = "retrieved_at")
  private LocalDate retrievedAt;

  protected SourceDocument() {}

  public SourceDocument(
      DatasetVersion datasetVersion,
      String code,
      DocumentType documentType,
      String title,
      String issuer,
      LocalDate publicationDate,
      String url,
      String fileSha256,
      LocalDate retrievedAt) {
    super(datasetVersion, code);
    this.documentType = documentType;
    this.title = title;
    this.issuer = issuer;
    this.publicationDate = publicationDate;
    this.url = url;
    this.fileSha256 = fileSha256;
    this.retrievedAt = retrievedAt;
  }

  public DocumentType getDocumentType() {
    return documentType;
  }

  public String getTitle() {
    return title;
  }

  public String getIssuer() {
    return issuer;
  }

  public LocalDate getPublicationDate() {
    return publicationDate;
  }

  public String getUrl() {
    return url;
  }

  public String getFileSha256() {
    return fileSha256;
  }

  public LocalDate getRetrievedAt() {
    return retrievedAt;
  }
}
