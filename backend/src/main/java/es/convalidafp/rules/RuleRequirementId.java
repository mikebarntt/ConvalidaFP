package es.convalidafp.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Clave primaria compuesta de {@link RuleRequirement}: (regla, módulo). */
@Embeddable
public class RuleRequirementId implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "rule_id")
  private Long ruleId;

  @Column(name = "module_id")
  private Long moduleId;

  protected RuleRequirementId() {}

  public RuleRequirementId(Long ruleId, Long moduleId) {
    this.ruleId = ruleId;
    this.moduleId = moduleId;
  }

  public Long getRuleId() {
    return ruleId;
  }

  public Long getModuleId() {
    return moduleId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof RuleRequirementId that
        && Objects.equals(ruleId, that.ruleId)
        && Objects.equals(moduleId, that.moduleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleId, moduleId);
  }
}
