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

import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.internal.TestSystem2;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.qualitygate.QualityGateConditionDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.server.computation.task.projectanalysis.qualitygate.QualityGateStatus;
import org.sonar.server.qualitygate.QualityGateFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.measures.CoreMetrics.BUGS_KEY;
import static org.sonar.api.measures.CoreMetrics.QUALITY_GATE_DETAILS_KEY;
import static org.sonar.db.component.ComponentTesting.newDirectory;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newModuleDto;


public class LiveQualityGateComputerImplTest {

  private final System2 system2 = new TestSystem2().setNow(System.currentTimeMillis());

  @Rule
  public DbTester dbTester = DbTester.create(system2);

  @Test
  public void recalculation_0bugs() throws Exception {
    checkQualityGateChange(0d, "GT", "1", "2", QualityGateStatus.OK);
  }

  @Test
  public void recalculation_1bugs() throws Exception {
    checkQualityGateChange(1d, "GT", "1", "2", QualityGateStatus.OK);
  }

  @Test
  public void recalculation_2bugs() throws Exception {
    checkQualityGateChange(2d, "GT", "1", "2", QualityGateStatus.WARN);
  }

  @Test
  public void recalculation_3bugs() throws Exception {
    checkQualityGateChange(3d, "GT", "1", "2", QualityGateStatus.ERROR);
  }

  private void checkQualityGateChange(double numberBugs, String operator, String warningThreshold, String errorThreshold, QualityGateStatus expected) {
    OrganizationDto organization = dbTester.organizations().insert();
    ComponentDto project = dbTester.components().insertPrivateProject(organization);
    ComponentDto module = dbTester.components().insertComponent(newModuleDto(project));
    ComponentDto subModule = dbTester.components().insertComponent(newModuleDto(module));
    ComponentDto directory = dbTester.components().insertComponent(newDirectory(subModule, "src"));

    ComponentDto file1 = newFileDto(project, directory);
    dbTester.components().insertComponent(file1);
    dbTester.getDbClient().componentDao().selectByKey(dbTester.getSession(), file1.getKey());

    ComponentDto file2 = newFileDto(project, directory);
    dbTester.components().insertComponent(file2);

    dbTester.measures().insertMetric(metric -> metric.setKey(QUALITY_GATE_DETAILS_KEY));
    dbTester.measures().insertMetric(metric -> metric.setKey(ALERT_STATUS_KEY));
    dbTester.measures().insertMetric(metric -> metric.setKey(BUGS_KEY).setValueType("INT"));
    MetricDto bugsMetric = dbTester.getDbClient().metricDao().selectByKey(dbTester.getSession(), BUGS_KEY);

    LiveMeasureDto liveMeasureDto = new LiveMeasureDto()
      .setProjectUuid(project.uuid())
      .setComponentUuid(file1.uuid())
      .setMetricId(bugsMetric.getId())
      .setValue(numberBugs);
    dbTester.getDbClient().liveMeasureDao().insert(dbTester.getSession(), liveMeasureDto);

    QualityGateDto qualityGate = dbTester.qualityGates().insertQualityGate();
    dbTester.qualityGates().associateProjectToQualityGate(project, qualityGate);
    QualityGateConditionDto condition = new QualityGateConditionDto().setQualityGateId(qualityGate.getId())
      .setMetricId(bugsMetric.getId())
      .setMetricKey(bugsMetric.getKey())
      .setOperator(operator)
      .setWarningThreshold(warningThreshold)
      .setErrorThreshold(errorThreshold);
    dbTester.getDbClient().gateConditionDao().insert(condition, dbTester.getSession());
    dbTester.commit();

    LiveQualityGateComputerImpl underTest = new LiveQualityGateComputerImpl(dbTester.getDbClient(), new QualityGateFinder(dbTester.getDbClient()));
    underTest.recalculateQualityGate(dbTester.getSession(), project, Collections.singletonList(liveMeasureDto));

    MetricDto qualityGateStatusMetric = dbTester.getDbClient().metricDao().selectByKey(dbTester.getSession(), ALERT_STATUS_KEY);
    List<LiveMeasureDto> qualityGateStatusMeasure = dbTester.getDbClient().liveMeasureDao().selectByComponentUuids(dbTester.getSession(), Collections.singleton(project.uuid()), Collections.singleton(qualityGateStatusMetric.getId()));
    assertThat(qualityGateStatusMeasure).extracting(LiveMeasureDto::getDataAsString).containsExactly(expected.name());

    MetricDto qualityGateDetailsMetric = dbTester.getDbClient().metricDao().selectByKey(dbTester.getSession(), QUALITY_GATE_DETAILS_KEY);
    List<LiveMeasureDto> qualityGateDetailsMeasure = dbTester.getDbClient().liveMeasureDao().selectByComponentUuids(dbTester.getSession(), Collections.singleton(project.uuid()), Collections.singleton(qualityGateDetailsMetric.getId()));
    assertThat(qualityGateDetailsMeasure.stream().findFirst().get().getDataAsString()).isEqualTo("{\"level\":\""+expected+"\",\"conditions\":[{\"metric\":\""+ BUGS_KEY + "\",\"op\":\"GT\",\"warning\":\""+warningThreshold+"\",\"error\":\""+errorThreshold+"\",\"actual\":\""+((int) numberBugs)+"\",\"level\":\""+expected+"\"}],\"ignoredConditions\":false}");
  }
}