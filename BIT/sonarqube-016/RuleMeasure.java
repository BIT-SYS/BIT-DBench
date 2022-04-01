/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.api.measures;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RulePriority;

import javax.annotation.Nullable;

/**
 * @since 1.10
 */
public class RuleMeasure extends Measure {

  private RuleKey ruleKey;
  private RulePriority rulePriority;

  /**
   * This constructor is for internal use only. Please use static methods createForXXX().
   * @deprecated since 4.4 use {@link #RuleMeasure(Metric, RuleKey, RulePriority, Integer)}
   */
  @Deprecated
  public RuleMeasure(Metric metric, @Nullable Rule rule, @Nullable RulePriority rulePriority, @Nullable Integer ruleCategory) {
    this(metric, rule.ruleKey(), rulePriority, ruleCategory);
  }

  public RuleMeasure(Metric metric, @Nullable RuleKey ruleKey, @Nullable RulePriority rulePriority, @Nullable Integer ruleCategory) {
    super(metric);
    this.ruleKey = ruleKey;
    this.rulePriority = rulePriority;
  }

  public RuleKey ruleKey() {
    return ruleKey;
  }

  public RuleMeasure setRuleKey(RuleKey ruleKey) {
    this.ruleKey = ruleKey;
    return this;
  }

  /**
   * @deprecated since 4.4 use {@link #ruleKey()}
   */
  @Deprecated
  public Rule getRule() {
    return Rule.create(ruleKey.repository(), ruleKey.rule());
  }

  /**
   * @deprecated since 4.4 use {@link #setRuleKey()}
   */
  @Deprecated
  public RuleMeasure setRule(Rule rule) {
    this.ruleKey = rule.ruleKey();
    return this;
  }

  /**
   * @deprecated since 2.14 use {@link #getSeverity()} instead. See SONAR-1829.
   */
  @Deprecated
  public RulePriority getRulePriority() {
    return rulePriority;
  }

  /**
   * @since 2.14
   */
  public RulePriority getSeverity() {
    return rulePriority;
  }

  /**
   * @deprecated since 2.14 use {@link #setSeverity(org.sonar.api.rules.RulePriority)} instead. See SONAR-1829.
   */
  @Deprecated
  public RuleMeasure setRulePriority(RulePriority rulePriority) {
    this.rulePriority = rulePriority;
    return this;
  }

  /**
   * @since 2.14
   */
  public RuleMeasure setSeverity(RulePriority severity) {
    this.rulePriority = severity;
    return this;
  }

  /**
   * @deprecated since 2.5. See SONAR-2007.
   */
  @Deprecated
  public Integer getRuleCategory() {
    return null;
  }

  /**
   * @deprecated since 2.5. See SONAR-2007.
   */
  @Deprecated
  public void setRuleCategory(Integer ruleCategory) {
    // nothing
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj.getClass().equals(RuleMeasure.class))) {// NOSONAR should be refactored but kept in the current state
      // for the moment.
      return false;
    }
    if (this == obj) {
      return true;
    }
    RuleMeasure other = (RuleMeasure) obj;
    return new EqualsBuilder()
      .append(getMetric(), other.getMetric())
      .append(personId, other.personId)
      .append(ruleKey, other.ruleKey)
      .isEquals();
  }

  @Override
  public RuleMeasure setValue(@Nullable Double v) {
    return (RuleMeasure) super.setValue(v);
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(getMetric())
      .append(personId)
      .append(ruleKey)
      .toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("metric", metric)
      .append("personId", personId)
      .append("ruleKey", ruleKey)
      .append("value", value)
      .append("data", data)
      .append("description", description)
      .append("alertStatus", alertStatus)
      .append("alertText", alertText)
      .append("tendency", tendency)
      .append("severity", rulePriority)
      .toString();
  }

  /**
   * @deprecated since 4.4 use {@link #createForRule(Metric, RuleKey, Double)}
   */
  @Deprecated
  public static RuleMeasure createForRule(Metric metric, Rule rule, @Nullable Double value) {
    return new RuleMeasure(metric, rule, null, null).setValue(value);
  }

  public static RuleMeasure createForRule(Metric metric, RuleKey ruleKey, @Nullable Double value) {
    return new RuleMeasure(metric, ruleKey, null, null).setValue(value);
  }

  public static RuleMeasure createForPriority(Metric metric, RulePriority priority, @Nullable Double value) {
    return new RuleMeasure(metric, (RuleKey) null, priority, null).setValue(value);
  }

  /**
   * @deprecated since 2.5. See SONAR-2007.
   */
  @Deprecated
  public static RuleMeasure createForCategory(Metric metric, Integer category, @Nullable Double value) {
    return new RuleMeasure(metric, (RuleKey) null, null, category).setValue(value);
  }
}
