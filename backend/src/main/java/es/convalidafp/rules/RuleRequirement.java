package es.convalidafp.rules;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.FpModule;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/** Un módulo exigido por una regla (rule_requirements.csv). Clave compuesta: regla y módulo. */
@Entity
@Table(name = "rule_requirement")
public class RuleRequirement {

  @EmbeddedId private RuleRequirementId id = new RuleRequirementId();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("ruleId")
  @JoinColumn(name = "rule_id", nullable = false)
  private RecognitionRule rule;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @MapsId("moduleId")
  @JoinColumn(name = "module_id", nullable = false)
  private FpModule module;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dataset_version_id", nullable = false, updatable = false)
  private DatasetVersion datasetVersion;

  protected RuleRequirement() {}

  RuleRequirement(RecognitionRule rule, FpModule module) {
    this.rule = rule;
    this.module = module;
    this.datasetVersion = rule.getDatasetVersion();
  }

  public RuleRequirementId getId() {
    return id;
  }

  public RecognitionRule getRule() {
    return rule;
  }

  public FpModule getModule() {
    return module;
  }

  public DatasetVersion getDatasetVersion() {
    return datasetVersion;
  }
}
