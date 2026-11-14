package es.convalidafp.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Universidad (universities.csv). */
@Entity
@Table(name = "university")
public class University extends VersionedEntity {

  /** Titularidad. */
  public enum Ownership {
    PUBLICA,
    PRIVADA
  }

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "short_name")
  private String shortName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "ownership", nullable = false)
  private Ownership ownership;

  @Column(name = "region")
  private String region;

  @Column(name = "website_url", length = 1000)
  private String websiteUrl;

  protected University() {}

  public University(
      DatasetVersion datasetVersion,
      String code,
      String name,
      String shortName,
      Ownership ownership,
      String region,
      String websiteUrl) {
    super(datasetVersion, code);
    this.name = name;
    this.shortName = shortName;
    this.ownership = ownership;
    this.region = region;
    this.websiteUrl = websiteUrl;
  }

  public String getName() {
    return name;
  }

  public String getShortName() {
    return shortName;
  }

  public Ownership getOwnership() {
    return ownership;
  }

  public String getRegion() {
    return region;
  }

  public String getWebsiteUrl() {
    return websiteUrl;
  }
}
