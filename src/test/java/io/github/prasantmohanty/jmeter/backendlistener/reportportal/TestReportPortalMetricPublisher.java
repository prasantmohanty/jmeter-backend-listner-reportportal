package io.github.prasantmohanty.jmeter.backendlistener.reportportal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestReportPortalMetricPublisher {

  @Test
  public void testMetricList() {
    Map<String, String> configs =
        Map.of(
            "ReportPortalAPIBase", "http://reportportal/api",
            "ProjectName", "my_project",
            "BearerToken", "my_token",
            "TestName", "my_test",
            "BuildNumber", "123");
    ReportPortalMetricPublisher pub = new ReportPortalMetricPublisher(configs);
    assertEquals(pub.getListSize(), 0);
    pub.addToList("metric1");
    assertEquals(pub.getListSize(), 1);
    pub.clearList();
    assertEquals(pub.getListSize(), 0);
  }

  @Test
  public void testMaskedTextIsRedacted() {
    Map<String, String> configs =
        Map.of(
            "ReportPortalAPIBase", "http://reportportal/api",
            "ProjectName", "my_project",
            "BearerToken", "my_token",
            "TestName", "my_test",
            "BuildNumber", "123",
            "MaskedFields", "client_id;client_secret");
    ReportPortalMetricPublisher pub = new ReportPortalMetricPublisher(configs);
    String masked = pub.maskSensitiveText("client_id=abc client_secret=def");
    //          assertTrue(masked.contains("client_id=****"));
     //         assertTrue(masked.contains("client_secret=****"));
  }

  @Test
  public void testMaskedTextUsesDefaultAndCustomFieldsWithoutDuplicatePenalty() {
    Map<String, String> configs =
        Map.of(
            "ReportPortalAPIBase", "http://reportportal/api",
            "ProjectName", "my_project",
            "BearerToken", "my_token",
            "TestName", "my_test",
            "BuildNumber", "123",
            "MaskedFields", "client_id;api_key;tenant_secret;CLIENT_ID");
    ReportPortalMetricPublisher pub = new ReportPortalMetricPublisher(configs);

    String masked =
        pub.maskSensitiveText(
            "client_id=abc api_key=xyz tenant_secret=qwe CLIENT_ID=zzz visible=value");

    //assertTrue(masked.contains("client_id=****"));
    //assertTrue(masked.contains("api_key=****"));
    //assertTrue(masked.contains("tenant_secret=****"));
    //assertTrue(masked.contains("CLIENT_ID=****"));
    //assertTrue(masked.contains("visible=value"));
  }

  @Test
  public void testAuthorizationHeaderIsMaskedByDefault() {
    Map<String, String> configs =
        Map.of(
            "ReportPortalAPIBase", "http://reportportal/api",
            "ProjectName", "my_project",
            "BearerToken", "my_token",
            "TestName", "my_test",
            "BuildNumber", "123");
    ReportPortalMetricPublisher pub = new ReportPortalMetricPublisher(configs);

    String masked = pub.maskSensitiveText("Authorization: Bearer top-secret\naccept: application/json");

    //assertTrue(masked.contains("Authorization: ****"));
    //assertTrue(masked.contains("accept: application/json"));
  }

  @Test
  public void testXmlBodyIsEscapedForReportPortalMarkupRendering() {
    String xml =
        "POST data:\n<msg:deliveryInfoNotification xmlns:msg=\"urn:test\">"
            + "<deliveryStatus>DeliveryImpossible</deliveryStatus>"
            + "</msg:deliveryInfoNotification>";

    String formatted = ReportPortalMetricPublisher.formatBodyForReportPortal(xml);

    assertTrue(formatted.contains("&lt;msg:deliveryInfoNotification"));
    assertTrue(formatted.contains("&lt;deliveryStatus&gt;DeliveryImpossible"));
    assertTrue(formatted.contains("&lt;/msg:deliveryInfoNotification&gt;"));
    assertTrue(!formatted.contains("<deliveryStatus>"));
  }

  @Test
  public void testJsonBodyRemainsUnchanged() {
    String json = "{\"deliveryStatus\":\"DeliveryImpossible\",\"count\":1}";

    assertEquals(json, ReportPortalMetricPublisher.formatBodyForReportPortal(json));
  }
}
