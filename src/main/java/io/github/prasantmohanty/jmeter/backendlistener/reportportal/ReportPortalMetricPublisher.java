/*
 * Copyright 2026 Prasanta Mohanty.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.prasantmohanty.jmeter.backendlistener.reportportal;

import io.github.prasantmohanty.jmeter.backendlistener.junit.transform.DomXmlJUnitReportWriter;
import io.github.prasantmohanty.jmeter.backendlistener.junit.transform.JtlRecord;
import io.github.prasantmohanty.jmeter.backendlistener.model.LaunchImportRq;
import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class responsible for publishing metrics to Report Portal.
 *
 * @author prasantmohanty
 * @since 20190624
 */
class ReportPortalMetricPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ReportPortalMetricPublisher.class);

  private Map<String, String> reportPortalConfigs = new HashMap<>();
  private List<String> metricList;

  ReportPortalMetricPublisher(Map<String, String> reportPortalConfigs) {
    this.reportPortalConfigs = reportPortalConfigs;
    this.metricList = new LinkedList<>();
  }

  public Map<String, String> getReportPortalConfigs() {
    return this.reportPortalConfigs;
  }

  /**
   * This method returns the current size of the JSON documents list
   *
   * @return integer representing the size of the JSON documents list
   */
  public int getListSize() {
    return this.metricList.size();
  }

  /** This method clears the JSON documents list */
  public void clearList() {
    this.metricList.clear();
  }

  public void addToList(String metric) {
    this.metricList.add(metric);
  }

  public void publishMetrics() {

     logger.debug("####Number of metrics to publish: " + this.metricList.size());

    String timestamp =
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String junitReportFile =
        java.nio.file.Paths.get("")
            .toAbsolutePath()
            .resolve("junit" + timestamp + ".xml")
            .toString();

    logger.debug("####JUnit report file: " + junitReportFile);

    // Determine a sensible test suite name to embed in the JUnit XML.
    // Priority: reportPortalConfigs.TestSuiteName -> first metric's ThreadName ->
    // reportPortalConfigs.TestName -> "no_name"
    String testSuiteName = null;
    try {
      testSuiteName = getReportPortalConfigs().get("TestSuiteName");
      if (testSuiteName == null || testSuiteName.trim().isEmpty()) {
        if (this.metricList != null && this.metricList.size() > 0) {
          com.fasterxml.jackson.databind.ObjectMapper mapper =
              new com.fasterxml.jackson.databind.ObjectMapper();
          com.fasterxml.jackson.databind.JsonNode first = mapper.readTree(this.metricList.get(0));
          testSuiteName = first.path("ThreadName").asText();
        }
      }
    } catch (Exception e) {
      logger.debug("Unable to derive testSuiteName from first metric", e);
    }
    if (testSuiteName == null || testSuiteName.trim().isEmpty()) {
      testSuiteName = getReportPortalConfigs().get("TestName");
    }
    if (testSuiteName == null || testSuiteName.trim().isEmpty()) {
      testSuiteName = "no_name";
    }

    final DomXmlJUnitReportWriter writer =
        new DomXmlJUnitReportWriter(junitReportFile, testSuiteName);

    for (int i = 0; i < this.metricList.size(); i++) {
      String metricJson = this.metricList.get(i);
      logger.debug("####Publishing metric " + (i + 1) + ": " + metricJson);
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(metricJson);
        logger.debug("Parsed JSON node: " + node.toPrettyString());

        String sampleLabel = node.path("SampleLabel").asText("");
        String failureMessage = node.path("FailureMessage").asText("");
        String responseCode = node.path("ResponseCode").asText("");
        String responseMessage = node.path("ResponseMessage").asText("");
        String requestBody = node.path("RequestBody").asText("");
        String requestHeaders = node.path("RequestHeaders").asText("");
        String responseBody = node.path("ResponseBody").asText("");
        String responseHeaders = node.path("ResponseHeaders").asText("");

        logger.debug(
            "Parsed metric: SampleLabel={}, FailureMessage={}, ResponseCode={}, ResponseMessage={}",
            sampleLabel,
            failureMessage,
            responseCode,
            responseMessage);

        boolean success = isFailureMessageAbsent(failureMessage);
        logger.debug("Determined success status: {}", success);

        writer.write(
            new JtlRecord(
                sampleLabel,
                success,
                responseMessage,
                failureMessage,
                requestHeaders,
                requestBody,
                responseHeaders,
                responseBody,
                responseCode));
        logger.debug("Successfully wrote metric to JUnit report: {}", junitReportFile);

        // use sampleLabel, failureMessage, responseCode, responseMessage as needed
      } catch (Exception e) {
        logger.error("Failed to write metric JSON: {}", metricJson, e);
      }
    }
    try {
      writer.close();
      logger.debug("Closed JUnit report writer for file: {}", junitReportFile);
    } catch (java.io.IOException e) {
      logger.error("Failed to close JUnit report writer for file: {}", junitReportFile, e);
    }
    try {
      publishToReportPortal(junitReportFile);
      logger.debug("Published JUnit report to ReportPortal: {}", junitReportFile);
    } catch (Exception e) {
      logger.error("Failed to publish JUnit report to ReportPortal: {}", junitReportFile, e);
    }
  }

  public static boolean isFailureMessageAbsent(String failureMessage) {
    if (failureMessage == null) {
      return true;
    }
    // Split by any line break, trim each line, drop blanks and "null"
    String normalized =
        Arrays.stream(failureMessage.split("\\R"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !"null".equalsIgnoreCase(line))
            .collect(Collectors.joining()); // join without delimiters

    // If nothing meaningful remains, treat it as absent
    return normalized.isEmpty();
  }

  public void publishToReportPortal(String junitReportFile) {

    logger.debug("Preparing to publish JUnit report to ReportPortal: " + junitReportFile);
    File file = new File(junitReportFile);
    String description = "Imported via API";
    ReportPortalImportAPIClient client = new ReportPortalImportAPIClient(getReportPortalConfigs());
    logger.debug(
        "Created ReportPortalImportClient for project: "
            + getReportPortalConfigs().get("ProjectName"));

    LaunchImportRq rq =
        new LaunchImportRq()
            .setName(getReportPortalConfigs().get("TestName"))
            .setDescription(description)
            .setStartTime(Instant.now())
            .addAttribute("origin", "bulk-import", false)
            .addAttribute("framework", "junit", false);

    // Attach the testsuite name as an attribute so ReportPortal can index it with the launch
    try {
      String suiteAttr =
          (getReportPortalConfigs().get("TestSuiteName") != null
                  && !getReportPortalConfigs().get("TestSuiteName").trim().isEmpty())
              ? getReportPortalConfigs().get("TestSuiteName")
              : "";
      if (suiteAttr.isEmpty()) {
        // derive from generated junit file's root attribute which we set earlier (testSuiteName)
        // we can reuse the TestName as a sensible fallback
        suiteAttr = getReportPortalConfigs().get("TestName");
      }
      if (suiteAttr != null && !suiteAttr.trim().isEmpty()) {
        rq.addAttribute("testsuite", suiteAttr, false);
      }
    } catch (Exception e) {
      logger.debug("Failed to add testsuite attribute to LaunchImportRq", e);
    }

    try {
      String response = client.importLaunch(file, rq);
      logger.debug("Response from ReportPortal: " + response);
    } catch (Exception e) {
      logger.error("Failed to prepare LaunchImportRq", e);
    }
  }
}
