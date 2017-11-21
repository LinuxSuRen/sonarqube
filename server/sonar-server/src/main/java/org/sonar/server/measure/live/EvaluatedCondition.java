/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.measure.live;


import javax.annotation.Nullable;
import org.sonar.db.qualitygate.QualityGateConditionDto;
import org.sonar.server.computation.task.projectanalysis.measure.Measure;

import static java.util.Objects.requireNonNull;

public final class EvaluatedCondition {
  private final QualityGateConditionDto condition;
  private final Measure.Level level;
  private final String actualValue;

  public EvaluatedCondition(QualityGateConditionDto condition, Measure.Level level, @Nullable Object actualValue) {
    this.condition = requireNonNull(condition);
    this.level = requireNonNull(level);
    this.actualValue = actualValue == null ? "" : actualValue.toString();
  }

  public QualityGateConditionDto getCondition() {
    return condition;
  }

  public Measure.Level getLevel() {
    return level;
  }

  public String getActualValue() {
    return actualValue;
  }
}