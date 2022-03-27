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
package org.sonar.server.rule.index;

import com.google.common.base.Preconditions;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.server.search.IndexField;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.util.Collection;

public class RuleQuery {

  private String key;
  private String queryText;
  private Collection<String> languages;
  private Collection<String> repositories;
  private Collection<String> severities;
  private Collection<RuleStatus> statuses;
  private Collection<String> tags;
  private Collection<String> allOfTags;
  private Collection<String> debtCharacteristics;
  private Boolean hasDebtCharacteristic;
  private Boolean activation;
  private String qProfileKey;
  private boolean ascendingSort = true;
  private IndexField sortField;


  /**
   * TODO should not be public
   *
   * @see org.sonar.server.rule.RuleService#newRuleQuery()
   */
  public RuleQuery() {
  }

  @CheckForNull
  public String getQProfileKey() {
    return qProfileKey;
  }

  public RuleQuery setQProfileKey(String qProfileKey) {
    this.qProfileKey = qProfileKey;
    return this;
  }

  public RuleQuery setActivation(@Nullable Boolean activation) {
    this.activation = activation;
    return this;
  }

  @CheckForNull
  public Boolean getActivation() {
    return this.activation;
  }

  @CheckForNull
  public String getKey() {
    return key;
  }

  public RuleQuery setKey(@Nullable String key) {
    this.key = key;
    return this;
  }

  @CheckForNull
  public String getQueryText() {
    return queryText;
  }

  /**
   * Ignored if null or blank
   */
  public RuleQuery setQueryText(@Nullable String queryText) {
    this.queryText = queryText;
    return this;
  }

  @CheckForNull
  public Collection<String> getLanguages() {
    return languages;
  }

  public RuleQuery setLanguages(@Nullable Collection<String> languages) {
    this.languages = languages;
    return this;
  }

  @CheckForNull
  public Collection<String> getRepositories() {
    return repositories;
  }

  public RuleQuery setRepositories(@Nullable Collection<String> repositories) {
    this.repositories = repositories;
    return this;
  }

  @CheckForNull
  public Collection<String> getSeverities() {
    return severities;
  }

  public RuleQuery setSeverities(@Nullable Collection<String> severities) {
    if (severities != null) {
      for (String severity : severities) {
        Preconditions.checkArgument(Severity.ALL.contains(severity), "Unknown severity: " + severity);
      }
    }
    this.severities = severities;
    return this;
  }

  @CheckForNull
  public Collection<RuleStatus> getStatuses() {
    return statuses;
  }

  public RuleQuery setStatuses(@Nullable Collection<RuleStatus> statuses) {
    this.statuses = statuses;
    return this;
  }

  @CheckForNull
  public Collection<String> getTags() {
    return tags;
  }

  public RuleQuery setTags(@Nullable Collection<String> tags) {
    this.tags = tags;
    return this;
  }

  @CheckForNull
  public Collection<String> getAllOfTags() {
    return allOfTags;
  }

  public RuleQuery setAllOfTags(@Nullable Collection<String> tags) {
    this.allOfTags = tags;
    return this;
  }

  @CheckForNull
  public Collection<String> getDebtCharacteristics() {
    return debtCharacteristics;
  }

  public RuleQuery setDebtCharacteristics(@Nullable Collection<String> debtCharacteristics) {
    this.debtCharacteristics = debtCharacteristics;
    return this;
  }

  @CheckForNull
  public Boolean getHasDebtCharacteristic() {
    return hasDebtCharacteristic;
  }

  public RuleQuery setHasDebtCharacteristic(@Nullable Boolean b) {
    this.hasDebtCharacteristic = b;
    return this;
  }

  public IndexField getSortField() {
    return this.sortField;
  }

  public RuleQuery setSortField(@Nullable IndexField sf) {
    if(!sf.sortable()){
      throw new IllegalStateException("Field '"+sf.field()+"' is not sortable!");
    }
    this.sortField = sf;
    return this;
  }

  public boolean isAscendingSort() {
    return ascendingSort;
  }

  public RuleQuery setAscendingSort(boolean b) {
    this.ascendingSort = b;
    return this;
  }
}
