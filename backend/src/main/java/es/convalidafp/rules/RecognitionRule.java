package es.convalidafp.rules;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DegreeSubject;
import es.convalidafp.catalog.FpModule;
import es.convalidafp.catalog.VersionedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Regla de reconocimiento (recognition_rules.csv) con los módulos que exige
 * (rule_requirements.csv). Las reglas son datos: aquí no hay lógica de simulación.
 */
@Entity
@Table(name = "recognition_rule")
public class RecognitionRule extends VersionedEntity {

  /** Qué reconoce la regla. */
  public enum TargetType {
    SUBJECT,
    OPTATIVE_CREDITS
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "table_id", nullable = false)
  private RecognitionTable recognitionTable;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "target_type", nullable = false)
  private TargetType targetType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_subject_id")
  private DegreeSubject targetSubject;

  @Column(name = "recognized_ects", precision = 6, scale = 2)
  private BigDecimal recognizedEcts;

  @Column(name = "is_conditional", nullable = false)
  private boolean conditional;

  @Column(name = "condition_text", length = 1000)
  private String conditionText;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id")
  private SourceDocument source;

  @Column(name = "source_page", nullable = false)
  private int sourcePage;

  @Column(name = "source_excerpt", length = 200)
  private String sourceExcerpt;

  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<RuleRequirement> requirements = new LinkedHashSet<>();

  protected RecognitionRule() {}

  public RecognitionRule(
      DatasetVersion datasetVersion,
      String code,
      RecognitionTable recognitionTable,
      TargetType targetType,
      DegreeSubject targetSubject,
      BigDecimal recognizedEcts,
      boolean conditional,
      String conditionText,
      SourceDocument source,
      int sourcePage,
      String sourceExcerpt) {
    super(datasetVersion, code);
    this.recognitionTable = recognitionTable;
    this.targetType = targetType;
    this.targetSubject = targetSubject;
    this.recognizedEcts = recognizedEcts;
    this.conditional = conditional;
    this.conditionText = conditionText;
    this.source = source;
    this.sourcePage = sourcePage;
    this.sourceExcerpt = sourceExcerpt;
  }

  /** Añade un módulo exigido. Todos los módulos de una regla son obligatorios (Y). */
  public void require(FpModule module) {
    requirements.add(new RuleRequirement(this, module));
  }

  /**
   * Fuente efectiva: la propia de la regla si la tiene y, si no, la de su tabla. {@code sourcePage}
   * se refiere siempre a esta fuente.
   */
  public SourceDocument getEffectiveSource() {
    return source != null ? source : recognitionTable.getSource();
  }

  public RecognitionTable getRecognitionTable() {
    return recognitionTable;
  }

  public TargetType getTargetType() {
    return targetType;
  }

  public DegreeSubject getTargetSubject() {
    return targetSubject;
  }

  public BigDecimal getRecognizedEcts() {
    return recognizedEcts;
  }

  public boolean isConditional() {
    return conditional;
  }

  public String getConditionText() {
    return conditionText;
  }

  /** Fuente propia de la regla; {@code null} si hereda la de su tabla. */
  public SourceDocument getSource() {
    return source;
  }

  public int getSourcePage() {
    return sourcePage;
  }

  public String getSourceExcerpt() {
    return sourceExcerpt;
  }

  public Set<RuleRequirement> getRequirements() {
    return Collections.unmodifiableSet(requirements);
  }
}
