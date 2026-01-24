package io.github.prasantmohanty.jmeter.backendlistener.reportportal;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

public class TestReportPortalMetricPublisher {

  @Test
  public void testMetricList() {
    Map<String, String> configs = Map.of(
        "ReportPortalAPIBase", "http://reportportal/api",
        "ProjectName", "my_project",
        "BearerToken", "my_token",
        "TestName", "my_test",
        "BuildNumber", "123"
    );
    ReportPortalMetricPublisher pub = new ReportPortalMetricPublisher(configs);
    assertEquals(pub.getListSize(), 0);
    pub.addToList("metric1");
    assertEquals(pub.getListSize(), 1);
    pub.clearList();
    assertEquals(pub.getListSize(), 0);
  }
}
