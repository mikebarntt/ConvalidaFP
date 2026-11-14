package es.convalidafp.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Respuestas de la API para el catálogo. Un valor null significa «desconocido». */
public final class CatalogDtos {

  private CatalogDtos() {}

  /** Versión del dataset de la que sale una respuesta. */
  public record DatasetInfo(Long versionId, String academicYear, Instant publishedAt) {}

  /** Versión publicada vigente y cursos con datos publicados. */
  public record DatasetResponse(
      Long versionId, String academicYear, Instant publishedAt, List<String> availableYears) {}

  public record CycleDto(
      String id,
      String name,
      String acronym,
      String professionalFamily,
      String regulationRef,
      BigDecimal totalEcts) {}

  public record UniversityDto(
      String id, String name, String shortName, String ownership, String region, String websiteUrl) {}

  /**
   * Grado.
   *
   * @param tableStatus solo al filtrar por ciclo: VIGENTE (hay tabla firmada) o SIN_CONVENIO
   *     (verificado que no la hay)
   */
  public record DegreeDto(
      String id,
      String universityId,
      String universityName,
      String name,
      String degreeType,
      String center,
      BigDecimal totalEcts,
      String planCode,
      String url,
      String tableStatus) {

    DegreeDto withTableStatus(String status) {
      return new DegreeDto(
          id, universityId, universityName, name, degreeType, center, totalEcts, planCode, url, status);
    }
  }

  public record SubjectDto(
      String id, String name, BigDecimal ects, String subjectType, Integer courseYear, String officialCode) {}

  /** Documento oficial. */
  public record SourceDto(
      String id,
      String documentType,
      String title,
      String issuer,
      LocalDate publicationDate,
      String url,
      String fileSha256,
      LocalDate retrievedAt) {}
}
