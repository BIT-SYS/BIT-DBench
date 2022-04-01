/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.batch.components;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.sonar.api.CoreProperties;
import org.sonar.api.database.model.Snapshot;
import org.sonar.api.utils.DateUtils;

import java.util.Date;

public class PastSnapshot {

  private int index;
  private String mode, modeParameter;
  private Snapshot projectSnapshot;
  private Date targetDate;

  public PastSnapshot(String mode, Date targetDate, Snapshot projectSnapshot) {
    this.mode = mode;
    this.targetDate = targetDate;
    this.projectSnapshot = projectSnapshot;
  }

  public PastSnapshot(String mode, Date targetDate) {
    this(mode, targetDate, null);
  }

  /**
   * See SONAR-2428 : even if previous analysis does not exist (no snapshot and no target date), we should perform comparison.
   */
  public PastSnapshot(String mode) {
    this(mode, null, null);
  }

  public PastSnapshot setIndex(int index) {
    this.index = index;
    return this;
  }

  public int getIndex() {
    return index;
  }

  public boolean isRelatedToSnapshot() {
    return projectSnapshot != null;
  }

  public Snapshot getProjectSnapshot() {
    return projectSnapshot;
  }

  public Date getDate() {
    return (projectSnapshot != null ? projectSnapshot.getCreatedAt() : null);
  }

  public String getMode() {
    return mode;
  }

  public String getModeParameter() {
    return modeParameter;
  }

  public PastSnapshot setModeParameter(String s) {
    this.modeParameter = s;
    return this;
  }

  Integer getProjectSnapshotId() {
    return (projectSnapshot != null ? projectSnapshot.getId() : null);
  }

  public String getQualifier() {
    return (projectSnapshot != null ? projectSnapshot.getQualifier() : null);
  }

  public Date getTargetDate() {
    return targetDate;
  }

  @Override
  public String toString() {
    if (StringUtils.equals(mode, CoreProperties.TIMEMACHINE_MODE_VERSION)) {
      String label = String.format("Compare to version %s", modeParameter);
      if (getTargetDate() != null) {
        label += String.format(" (%s)", DateUtils.formatDate(getTargetDate()));
      }
      return label;
    }
    if (StringUtils.equals(mode, CoreProperties.TIMEMACHINE_MODE_DAYS)) {
      String label = String.format("Compare over %s days (%s", modeParameter, DateUtils.formatDate(getTargetDate()));
      if (isRelatedToSnapshot()) {
        label += ", analysis of " + getDate();
      }
      label += ")";
      return label;
    }
    if (StringUtils.equals(mode, CoreProperties.TIMEMACHINE_MODE_PREVIOUS_ANALYSIS)) {
      String label = "Compare to previous analysis";
      if (isRelatedToSnapshot()) {
        label += String.format(" (%s)", DateUtils.formatDate(getDate()));
      }
      return label;
    }
    if (StringUtils.equals(mode, CoreProperties.TIMEMACHINE_MODE_DATE)) {
      String label = "Compare to date " + DateUtils.formatDate(getTargetDate());
      if (isRelatedToSnapshot()) {
        label += String.format(" (analysis of %s)", DateUtils.formatDate(getDate()));
      }
      return label;
    }
    return ReflectionToStringBuilder.toString(this);
  }

}
