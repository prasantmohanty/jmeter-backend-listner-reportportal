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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.prasantmohanty.jmeter.backendlistener.junit.transform.DomXmlJUnitReportWriter;
import io.github.prasantmohanty.jmeter.backendlistener.junit.transform.JtlRecord;
import io.github.prasantmohanty.jmeter.backendlistener.model.LaunchImportRq;

import java.io.File;
import java.time.Instant;
import java.util.Map; 

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 *  A class responsible for publishing metrics to Report Portal.
 *
 * @author prasantmohanty
 * @since 20190624
 * 
 * 
 * 
 */
class ReportPortalMetricPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ReportPortalMetricPublisher.class);

  private Map<String, String> reportPortalConfigs = new HashMap<>();
  private List<String> metricList;

  ReportPortalMetricPublisher(Map<String, String> reportPortalConfigs) {
    this.reportPortalConfigs =  reportPortalConfigs;
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

  /**
   * This method adds a metric to the list (metricList).
   *
   * @param metric String parameter representing a JSON document for Kafka
   */
  public void addToList(String metric) {
    this.metricList.add(metric);
  }

  

public void publishMetrics() {

    long time = System.currentTimeMillis();
    logger.debug("####Number of metrics to publish: " + this.metricList.size());
 
    String timestamp = java.time.LocalDateTime.now()
          .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String junitReportFile = java.nio.file.Paths.get("")
          .toAbsolutePath()
          .resolve("junit" + timestamp + ".xml")
          .toString();
    
    logger.debug("####JUnit report file: " + junitReportFile);

    final DomXmlJUnitReportWriter writer
            = new DomXmlJUnitReportWriter(junitReportFile, "");

    for (int i = 0; i < this.metricList.size(); i++) {
      String metricJson = this.metricList.get(i);
      logger.debug("####Publishing metric " + (i + 1) + ": " + metricJson);
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(metricJson);
        logger.debug("Parsed JSON node: " + node.toPrettyString()); 
        

        String sampleLabel = node.path("SampleLabel").asText("");
        String failureMessage = node.path("FailureMessage").asText("");
        String responseCode = node.path("ResponseCode").asText("");
        String responseMessage = node.path("ResponseMessage").asText("");

        logger.debug("Parsed metric: SampleLabel={}, FailureMessage={}, ResponseCode={}, ResponseMessage={}",
        sampleLabel, failureMessage, responseCode, responseMessage);

      boolean success = isFailureMessageAbsent(failureMessage);
      logger.debug("Determined success status: {}", success);

      writer.write(new JtlRecord(sampleLabel, success, responseMessage, failureMessage));
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
    try{
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
      String normalized = Arrays.stream(failureMessage.split("\\R"))
              .map(String::trim)
              .filter(line -> !line.isEmpty())
              .filter(line -> !"null".equalsIgnoreCase(line))
              .collect(Collectors.joining()); // join without delimiters
  
      // If nothing meaningful remains, treat it as absent
      return normalized.isEmpty();
  }
  

  public void publishToReportPortal(String junitReportFile) {
    String apiBaseUrl = "http://localhost:8080/api/";      // e.g., https://rp.example.com/api
    String project = "default_personal";         // e.g., my_project
    String token = "MyRPKEY_A8UB7HbTRY6UpRDRqeFcCjJ6-aKw9Cu34MV6blvRaNJMzwLxXo4FLNUdZFHmdPtp";// JWT or API Key
    String launchName = "JMeter Test";
    logger.debug("Preparing to publish JUnit report to ReportPortal: " + junitReportFile);
    File file = new File(junitReportFile);
    String description = "Imported via API";
    ReportPortalImportAPIClient client = new ReportPortalImportAPIClient(getReportPortalConfigs());
    logger.debug("Created ReportPortalImportClient for project: " + project);
    
    LaunchImportRq rq = new LaunchImportRq()
        .setName(launchName)
        .setDescription(description)
        .setStartTime(Instant.now())
        .addAttribute("origin", "bulk-import", false)
        .addAttribute("framework", "junit", false);

    try {
      String response = client.importLaunch(file, rq);
    logger.debug("Response from ReportPortal: " + response);
    } catch (Exception e) {
        logger.error("Failed to prepare LaunchImportRq", e);
    }
    
 
  }
}
