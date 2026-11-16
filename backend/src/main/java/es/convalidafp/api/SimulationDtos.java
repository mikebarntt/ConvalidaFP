package es.convalidafp.api;

import static es.convalidafp.api.Numbers.plain;

import es.convalidafp.api.CatalogDtos.CycleDto;
import es.convalidafp.api.CatalogDtos.DatasetInfo;
import es.convalidafp.api.CatalogDtos.DegreeDto;
import es.convalidafp.simulation.SimulationResult;
import es.convalidafp.simulation.SimulationResult.NoAgreement;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.simulation.SimulationResult.SourceCitation;
import es.convalidafp.simulation.SourceRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Respuestas de la API para simulaciones, comparaciones y exploración. */
public final class SimulationDtos {

  /** Regla inquebrantable 5: todo resultado indica que es orientativo y que conlleva tasas. */
  public static final String NOTICE =
      "Resultado orientativo, no vinculante: la resolución final corresponde a la universidad y el"
          + " reconocimiento de créditos conlleva tasas.";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private SimulationDtos() {}

  /**
   * Resultado de una simulación.
   *
   * @param outcome RECOGNIZED (hay tabla vigente y reconocimiento calculado), NO_AGREEMENT
   *     (verificado que no existe tabla firmada) o NO_DATA (sin datos para esa combinación)
   * @param notice aviso de que el resultado es orientativo
   * @param capped true si la suma de las reglas supera el tope: el total se limita y la universidad
   *     decide qué asignaturas entran. null si no hay reconocimiento calculado
   * @param recognizedPercentage porcentaje del grado que se reconoce, con dos decimales
   * @param pendingEcts total_ects del grado menos los reconocidos, aunque la lista de asignaturas
   *     del grado esté incompleta
   * @param subjectsComplete true si el dataset trae todas las asignaturas del grado. Con false solo
   *     trae las que aparecen en las tablas de reconocimiento, y remainingByYear va vacío
   * @param remainingByYear asignaturas del grado no reconocidas, por curso; vacío si
   *     subjectsComplete es false
   * @param noAgreement solo con NO_AGREEMENT: de dónde sale la verificación
   */
  public record SimulationResponse(
      String outcome,
      DatasetInfo dataset,
      CycleDto cycle,
      DegreeDto degree,
      String notice,
      String tableId,
      List<RecognizedSubjectDto> recognizedSubjects,
      List<OptativeCreditsDto> optativeCredits,
      List<ConditionalRecognitionDto> conditionalRecognitions,
      BigDecimal rulesTotalEcts,
      BigDecimal legalCapEcts,
      BigDecimal tableCapEcts,
      BigDecimal appliedCapEcts,
      BigDecimal recognizedEcts,
      Boolean capped,
      BigDecimal degreeTotalEcts,
      BigDecimal pendingEcts,
      BigDecimal recognizedPercentage,
      boolean subjectsComplete,
      List<RemainingYearDto> remainingByYear,
      NoAgreementDto noAgreement) {}

  /** Documento, página y fragmento literal de los que sale un reconocimiento. */
  public record CitationDto(
      String sourceId,
      String documentType,
      String title,
      LocalDate publicationDate,
      String url,
      int page,
      String excerpt) {}

  public record RecognizedSubjectDto(
      String subjectId,
      String name,
      BigDecimal ects,
      Integer courseYear,
      String ruleId,
      List<String> alternativeRuleIds,
      CitationDto source) {}

  public record OptativeCreditsDto(String ruleId, BigDecimal ects, CitationDto source) {}

  /**
   * Reconocimiento condicionado: se muestra aparte y no suma.
   *
   * @param conditionText null si el documento no especifica la condición
   */
  public record ConditionalRecognitionDto(
      String ruleId,
      String subjectId,
      String subjectName,
      BigDecimal ects,
      String conditionText,
      CitationDto source) {}

  public record RemainingYearDto(Integer courseYear, List<RemainingSubjectDto> subjects) {}

  public record RemainingSubjectDto(String id, String name, BigDecimal ects, String subjectType) {}

  public record NoAgreementDto(String tableId, CitationSourceDto source, String notes) {}

  /** Documento sin página: la verificación de que no hay convenio no cita una página. */
  public record CitationSourceDto(
      String sourceId, String documentType, String title, LocalDate publicationDate, String url) {}

  /** Comparación de un ciclo frente a varios grados. */
  public record ComparisonResponse(DatasetInfo dataset, CycleDto cycle, List<SimulationResponse> results) {}

  /** Grados que reconocen algo desde un ciclo, de más a menos ECTS. */
  public record ExploreResponse(DatasetInfo dataset, CycleDto cycle, String notice, List<ExploreEntryDto> results) {}

  public record ExploreEntryDto(
      DegreeDto degree,
      BigDecimal recognizedEcts,
      BigDecimal recognizedPercentage,
      boolean capped,
      BigDecimal rulesTotalEcts,
      int recognizedSubjectCount) {}

  // ---------- conversión desde el resultado del motor ----------

  static SimulationResponse toResponse(PublishedDataset dataset, SimulationResult result) {
    CycleDto cycle = dataset.requireCycle(result.input().cycleId());
    DegreeDto degree = dataset.requireDegree(result.input().degreeId());
    boolean subjectsComplete = dataset.subjectsComplete(degree.id());
    if (result instanceof Recognized r) {
      return new SimulationResponse(
          "RECOGNIZED",
          dataset.info(),
          cycle,
          degree,
          NOTICE,
          r.tableId(),
          r.recognizedSubjects().stream()
              .map(
                  s ->
                      new RecognizedSubjectDto(
                          s.subjectId(),
                          s.name(),
                          plain(s.ects()),
                          s.courseYear(),
                          s.ruleId(),
                          s.alternativeRuleIds(),
                          citation(s.citation())))
              .toList(),
          r.optativeCredits().stream()
              .map(c -> new OptativeCreditsDto(c.ruleId(), plain(c.ects()), citation(c.citation())))
              .toList(),
          r.conditionalRecognitions().stream()
              .map(
                  c ->
                      new ConditionalRecognitionDto(
                          c.ruleId(),
                          c.subjectId(),
                          c.subjectName(),
                          plain(c.ects()),
                          c.conditionText(),
                          citation(c.citation())))
              .toList(),
          plain(r.rulesTotalEcts()),
          plain(r.legalCapEcts()),
          plain(r.tableCapEcts()),
          plain(r.appliedCapEcts()),
          plain(r.recognizedEcts()),
          r.capped(),
          plain(r.degreeTotalEcts()),
          plain(r.pendingEcts()),
          percentage(r),
          subjectsComplete,
          // Con la lista de asignaturas incompleta, las «restantes» serían engañosas: no se devuelven.
          (subjectsComplete ? r.remainingByYear() : List.<SimulationResult.RemainingYear>of()).stream()
              .map(
                  y ->
                      new RemainingYearDto(
                          y.courseYear(),
                          y.subjects().stream()
                              .map(s -> new RemainingSubjectDto(s.id(), s.name(), plain(s.ects()), s.subjectType()))
                              .toList()))
              .toList(),
          null);
    }
    NoAgreementDto noAgreement =
        result instanceof NoAgreement n ? new NoAgreementDto(n.tableId(), source(n.source()), n.notes()) : null;
    return new SimulationResponse(
        noAgreement != null ? "NO_AGREEMENT" : "NO_DATA",
        dataset.info(),
        cycle,
        degree,
        NOTICE,
        noAgreement != null ? noAgreement.tableId() : null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null,
        null,
        null,
        null,
        null,
        degree.totalEcts(),
        null,
        null,
        subjectsComplete,
        List.of(),
        noAgreement);
  }

  static ExploreEntryDto toExploreEntry(PublishedDataset dataset, Recognized r) {
    return new ExploreEntryDto(
        dataset.requireDegree(r.input().degreeId()),
        plain(r.recognizedEcts()),
        percentage(r),
        r.capped(),
        plain(r.rulesTotalEcts()),
        r.recognizedSubjects().size());
  }

  /** Porcentaje del grado reconocido. Es un valor de presentación: aquí sí se redondea. */
  private static BigDecimal percentage(Recognized r) {
    return plain(r.recognizedEcts().multiply(HUNDRED).divide(r.degreeTotalEcts(), 2, RoundingMode.HALF_UP));
  }

  private static CitationDto citation(SourceCitation citation) {
    SourceRef source = citation.source();
    return source == null
        ? new CitationDto(null, null, null, null, null, citation.page(), citation.excerpt())
        : new CitationDto(
            source.id(),
            source.documentType(),
            source.title(),
            source.publicationDate(),
            source.url(),
            citation.page(),
            citation.excerpt());
  }

  private static CitationSourceDto source(SourceRef source) {
    return source == null
        ? null
        : new CitationSourceDto(
            source.id(), source.documentType(), source.title(), source.publicationDate(), source.url());
  }
}
