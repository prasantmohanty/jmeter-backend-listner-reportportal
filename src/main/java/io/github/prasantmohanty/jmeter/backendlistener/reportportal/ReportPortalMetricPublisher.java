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
import io.github.prasantmohanty.jmeter.backendlistener.model.Attribute;
import io.github.prasantmohanty.jmeter.backendlistener.model.LaunchImportRq;
import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class responsible for publishing metrics to Report Portal.
 *
 * @author prasantmohanty
 * @since 20250624
 */
class ReportPortalMetricPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ReportPortalMetricPublisher.class);
  private static final int MAX_FIELD_LOG_LENGTH = 12000;
  private static final int MAX_SUMMARY_LOG_LENGTH = 700;
  private static final DateTimeFormatter SAMPLE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

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
    try {
      publishToReportPortal();
      logger.debug("Published report to ReportPortal");
    } catch (Exception e) {
      logger.error("Failed to publish report to ReportPortal", e);
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

  public void publishToReportPortal() {
    
    // New v2 API flow: create a launch and create items/logs via /api/v2 endpoints
    logger.debug("Publishing metrics via ReportPortal v2 API");
    ReportPortalAPIClient apiClient = new ReportPortalAPIClient(getReportPortalConfigs());

    LaunchImportRq launchRq =
        new LaunchImportRq()
            .setName(getReportPortalConfigs().get("TestName"))
            .setDescription("Imported via API")
        .setStartTime(resolveLaunchStartTime())
            .addAttribute("origin", "bulk-import", false)
            .addAttribute("framework", "metrics", false);

    try {
      String suiteAttr =
          (getReportPortalConfigs().get("TestSuiteName") != null
                  && !getReportPortalConfigs().get("TestSuiteName").trim().isEmpty())
              ? getReportPortalConfigs().get("TestSuiteName")
              : "";
      if (suiteAttr.isEmpty()) {
        suiteAttr = getReportPortalConfigs().get("TestName");
      }
      if (suiteAttr != null && !suiteAttr.trim().isEmpty()) {
        launchRq.addAttribute("testsuite", suiteAttr, false);
      }
    } catch (Exception e) {
      logger.debug("Failed to add testsuite attribute to LaunchImportRq", e);
    }

    String launchId = null;
    try {
      launchId = apiClient.startLaunch(launchRq);
      logger.debug("Started launch in ReportPortal v2 with id/uuid: " + launchId);
    } catch (Exception e) {
      logger.error("Failed to start ReportPortal launch via v2 API", e);
      return;
    }

    String suiteName = resolveSuiteName();
    String suiteItemId = null;
    Instant suiteStart = resolveLaunchStartTime();
    Instant suiteEnd = resolveLaunchEndTime();
    try {
      List<Attribute> suiteAttrs = new ArrayList<>();
      suiteAttrs.add(new Attribute("layer", "suite", false));
      if (safe(getReportPortalConfigs().get("BuildNumber")).length() > 0) {
        suiteAttrs.add(
            new Attribute("build", getReportPortalConfigs().get("BuildNumber"), false));
      }
      suiteItemId =
          apiClient.startTestItem(
              launchId,
              suiteName,
              "JMeter suite container",
              "suite",
              suiteStart,
              "jmeter.suite." + normalizeForCodeRef(suiteName),
              buildUniqueId("suite", suiteName, suiteStart.toString()),
              suiteAttrs,
              Collections.emptyList());
      logger.debug("Created suite item: {}", suiteItemId);
    } catch (Exception e) {
      logger.error("Failed to create suite item; will continue with flat test item reporting", e);
    }

    boolean anyFailure = false;
    int suiteTotal = 0;
    int suitePassed = 0;
    int suiteFailed = 0;
    int suiteSkipped = 0;
    int suiteErrors = 0;

    // For each metric create a test item, optionally log failure, then finish the item
    for (int i = 0; i < this.metricList.size(); i++) {
      String metricJson = this.metricList.get(i);
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(metricJson);

        String sampleLabel = node.path("SampleLabel").asText("");
        String failureMessage = node.path("FailureMessage").asText("");
        String responseMessage = node.path("ResponseMessage").asText("");
        String requestHeaders = node.path("RequestHeaders").asText("");
        String requestBody = node.path("RequestBody").asText("");
        String responseHeaders = node.path("ResponseHeaders").asText("");
        String responseBody = node.path("ResponseBody").asText("");
        String responseCode = node.path("ResponseCode").asText("");
        String requestUrl = safe(node.path("URL").asText(""));
        String requestParameters = extractRequestParameters(requestUrl, requestBody);
        String assertionErrors = extractAssertionErrors(node);
        int totalCount = node.path("SampleCount").asInt(1);
        int failedCount = node.path("ErrorCount").asInt(0);
        boolean hasSampleSuccessful = node.has("SampleSuccessful") && !node.get("SampleSuccessful").isNull();
        boolean sampleSuccessful = hasSampleSuccessful && node.get("SampleSuccessful").asBoolean(true);
        boolean hasSuccess = node.has("Success") && !node.get("Success").isNull();
        boolean successFromSample = hasSuccess && node.get("Success").asBoolean(true);

        boolean success;
        if (hasSampleSuccessful) {
          success = sampleSuccessful;
        } else if (hasSuccess) {
          success = successFromSample;
        } else {
          success = isFailureMessageAbsent(failureMessage);
        }
        if (failedCount > 0) {
          success = false;
        }
        if (!success && failedCount == 0) {
          failedCount = 1;
        }
        if (totalCount < 1) {
          totalCount = 1;
        }
        int successCount = Math.max(totalCount - failedCount, 0);
        Instant itemStart = parseSampleInstant(node.path("SampleStartTime").asText(""), Instant.now());
        Instant itemEnd = parseSampleInstant(node.path("SampleEndTime").asText(""), itemStart);
        if (itemEnd.isBefore(itemStart)) {
          itemEnd = itemStart;
        }
        String itemStatus = toReportPortalStatus(success, responseCode, failureMessage);
        anyFailure =
          anyFailure
            || "failed".equals(itemStatus)
            || "interrupted".equals(itemStatus)
            || "cancelled".equals(itemStatus)
            || "stopped".equals(itemStatus);

        String itemId = null;
        try {
          String codeRef =
              safe(node.path("URL").asText(""));
          if (codeRef.isEmpty()) {
            codeRef = "jmeter.test." + normalizeForCodeRef(sampleLabel);
          }
          List<ReportPortalAPIClient.ItemParameter> parameters = new ArrayList<>();
          String threadName = safe(node.path("ThreadName").asText(""));
          if (!threadName.isEmpty()) {
            parameters.add(new ReportPortalAPIClient.ItemParameter("thread", threadName));
          }
          if (!responseCode.isEmpty()) {
            parameters.add(new ReportPortalAPIClient.ItemParameter("responseCode", responseCode));
          }
          List<Attribute> itemAttrs = new ArrayList<>();
          itemAttrs.add(new Attribute("layer", "test", false));
          if (!responseCode.isEmpty()) {
            itemAttrs.add(new Attribute("http.code", responseCode, false));
          }
          if (failedCount > 0) {
            itemAttrs.add(new Attribute("failed", String.valueOf(failedCount), false));
          }

          String uniqueId =
              buildUniqueId(
                  "test",
                  sampleLabel,
                  safe(node.path("ThreadName").asText("")),
                  node.path("SampleStartTime").asText(""));

          if (suiteItemId != null && !suiteItemId.trim().isEmpty()) {
            itemId =
                apiClient.startChildTestItem(
                    suiteItemId,
                    launchId,
                    sampleLabel,
                    "JMeter sample",
                    "step",
                    itemStart,
                    codeRef,
                    uniqueId,
                    itemAttrs,
                    parameters);
          } else {
            itemId =
                apiClient.startTestItem(
                    launchId,
                    sampleLabel,
                    "JMeter sample",
                    "step",
                    itemStart,
                    codeRef,
                    uniqueId,
                    itemAttrs,
                    parameters);
          }
          logger.debug("Created test item: " + itemId + " for sample: " + sampleLabel);
        } catch (Exception e) {
          logger.error("Failed to create test item for sample: " + sampleLabel, e);
          continue;
        }

        String detailMessage =
            buildItemDetailMessage(
                sampleLabel,
                success,
                responseMessage,
                failureMessage,
                requestHeaders,
                requestBody,
                responseHeaders,
                responseBody,
                requestUrl,
                requestParameters,
                assertionErrors,
                responseCode,
                totalCount,
                successCount,
                failedCount);
        String summaryMessage =
            buildSummaryMessage(
                sampleLabel,
                success,
                responseCode,
                responseMessage,
                failureMessage,
                totalCount,
                successCount,
                failedCount);
        try {
          apiClient.log(launchId, itemId, success ? "info" : "error", detailMessage, itemEnd);
        } catch (Exception e) {
          logger.warn("Failed to send detail log for item: " + itemId, e);
          try {
            apiClient.log(launchId, itemId, success ? "info" : "error", summaryMessage, itemEnd);
          } catch (Exception summaryEx) {
            logger.warn("Failed to send summary log for item: " + itemId, summaryEx);
          }
        }

        try {
          apiClient.finishTestItem(launchId, itemId, itemStatus, itemEnd);
          suiteTotal++;
          if ("passed".equals(itemStatus)) {
            suitePassed++;
          } else if ("skipped".equals(itemStatus)) {
            suiteSkipped++;
          } else if ("failed".equals(itemStatus)) {
            suiteFailed++;
          } else {
            // interrupted/cancelled/stopped and any future non-passed states
            suiteErrors++;
          }
        } catch (Exception e) {
          logger.error("Failed to finish test item: " + itemId, e);
        }
      } catch (Exception e) {
        logger.error("Failed to parse metric JSON for v2 publish: {}", metricJson, e);
      }
    }

    if (suiteItemId != null && !suiteItemId.trim().isEmpty()) {
      String suiteSummary =
          buildSuiteSummaryMessage(
              suiteName, suiteTotal, suitePassed, suiteFailed, suiteSkipped, suiteErrors);
      try {
        apiClient.log(launchId, suiteItemId, anyFailure ? "warn" : "info", suiteSummary, suiteEnd);
      } catch (Exception e) {
        logger.warn("Failed to send suite summary log for suite item: {}", suiteItemId, e);
      }
      try {
        apiClient.finishTestItem(launchId, suiteItemId, anyFailure ? "failed" : "passed", suiteEnd);
      } catch (Exception e) {
        logger.error("Failed to finish suite item: {}", suiteItemId, e);
      }
    }

    try {
      apiClient.finishLaunch(launchId, anyFailure ? "failed" : "passed", suiteEnd);
      logger.debug("Finished launch {}", launchId);
    } catch (Exception e) {
      logger.error("Failed to finish launch: {}", launchId, e);
    }
  }

  private static String buildItemDetailMessage(
      String sampleLabel,
      boolean success,
      String responseMessage,
      String failureMessage,
      String requestHeaders,
      String requestBody,
      String responseHeaders,
      String responseBody,
      String requestUrl,
      String requestParameters,
      String assertionErrors,
      String responseCode,
      int totalCount,
      int successCount,
      int failedCount) {
    StringBuilder sb = new StringBuilder(2048);
    sb.append("sampleLabel: ").append(safe(sampleLabel)).append('\n');
    sb.append("success: ").append(success).append('\n');
    sb.append("total: ").append(totalCount).append('\n');
    sb.append("successCount: ").append(successCount).append('\n');
    sb.append("failed: ").append(failedCount).append('\n');
    sb.append("requestUrl: ").append(safe(requestUrl)).append('\n');
    sb.append("requestParameters:\n").append(limit(safe(requestParameters))).append('\n');
    sb.append("responseCode: ").append(safe(responseCode)).append('\n');
    sb.append("responseMessage: ").append(safe(responseMessage)).append('\n');
    sb.append("failureMessage: ").append(limit(safe(failureMessage))).append('\n');
    sb.append("assertionErrors:\n").append(limit(safe(assertionErrors))).append('\n');
    sb.append("requestHeaders:\n").append(limit(safe(requestHeaders))).append('\n');
    sb.append("requestBody:\n").append(limit(safe(requestBody))).append('\n');
    sb.append("responseHeaders:\n").append(limit(safe(responseHeaders))).append('\n');
    sb.append("responseBody:\n").append(limit(safe(responseBody)));
    return sb.toString();
  }

  private static String safe(String value) {
    if (value == null) {
      return "";
    }
    // Remove control characters that can break proxies/gateways while preserving line breaks.
    return value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
  }

  private static String limit(String value) {
    if (value == null) {
      return "";
    }
    if (value.length() <= MAX_FIELD_LOG_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_FIELD_LOG_LENGTH)
        + "\n...[truncated "
        + (value.length() - MAX_FIELD_LOG_LENGTH)
        + " chars]";
  }

  private static String buildSummaryMessage(
      String sampleLabel,
      boolean success,
      String responseCode,
      String responseMessage,
      String failureMessage,
      int totalCount,
      int successCount,
      int failedCount) {
    StringBuilder sb = new StringBuilder(512);
    sb.append("summary\n");
    sb.append("sampleLabel: ").append(safe(sampleLabel)).append('\n');
    sb.append("success: ").append(success).append('\n');
    sb.append("total: ").append(totalCount).append('\n');
    sb.append("successCount: ").append(successCount).append('\n');
    sb.append("failed: ").append(failedCount).append('\n');
    sb.append("responseCode: ").append(safe(responseCode)).append('\n');
    sb.append("responseMessage: ").append(safe(responseMessage)).append('\n');
    sb.append("failureMessage: ").append(safe(failureMessage));
    return sb.toString();
  }

  private static String extractRequestParameters(String requestUrl, String requestBody) {
    StringBuilder sb = new StringBuilder();
    String query = extractQueryFromUrl(requestUrl);
    if (!query.isEmpty()) {
      sb.append("query: ").append(query);
    }
    String bodyParams = extractBodyArguments(requestBody);
    if (!bodyParams.isEmpty()) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append("body: ").append(bodyParams);
    }
    return sb.toString();
  }

  private static String extractQueryFromUrl(String requestUrl) {
    if (requestUrl == null || requestUrl.trim().isEmpty()) {
      return "";
    }
    try {
      URI uri = URI.create(requestUrl.trim());
      String query = uri.getRawQuery();
      if (query == null || query.isEmpty()) {
        return "";
      }
      String[] parts = query.split("&");
      List<String> decodedParts = new ArrayList<>();
      for (String p : parts) {
        decodedParts.add(URLDecoder.decode(p, StandardCharsets.UTF_8.name()));
      }
      return String.join("&", decodedParts);
    } catch (Exception e) {
      return "";
    }
  }

  private static String extractBodyArguments(String requestBody) {
    String body = safe(requestBody);
    if (body.isEmpty()) {
      return "";
    }
    int idx = body.indexOf("Arguments:");
    if (idx >= 0) {
      String argsPart = body.substring(idx + "Arguments:".length()).trim();
      int end = argsPart.indexOf('\n');
      return end >= 0 ? argsPart.substring(0, end).trim() : argsPart;
    }
    return "";
  }

  private static String extractAssertionErrors(com.fasterxml.jackson.databind.JsonNode node) {
    com.fasterxml.jackson.databind.JsonNode assertionNode = node.path("AssertionResults");
    if (assertionNode.isMissingNode() || assertionNode.isNull()) {
      return "";
    }
    if (assertionNode.isArray()) {
      List<String> errors = new ArrayList<>();
      for (com.fasterxml.jackson.databind.JsonNode entry : assertionNode) {
        boolean isFailure = entry.path("failure").asBoolean(false);
        String message = entry.path("failureMessage").asText("");
        String name = entry.path("name").asText("");
        if (isFailure || !message.trim().isEmpty()) {
          errors.add((name.isEmpty() ? "assertion" : name) + ": " + message);
        }
      }
      return String.join("\n", errors);
    }
    return assertionNode.toString();
  }

  private String resolveSuiteName() {
    String suiteName = safe(getReportPortalConfigs().get("TestSuiteName"));
    if (!suiteName.isEmpty()) {
      return suiteName;
    }
    String testName = safe(getReportPortalConfigs().get("TestName"));
    return testName.isEmpty() ? "JMeter Suite" : testName;
  }

  private Instant resolveLaunchStartTime() {
    Instant earliest = null;
    for (String metricJson : this.metricList) {
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(metricJson);
        Instant candidate = parseSampleInstant(node.path("SampleStartTime").asText(""), null);
        if (candidate != null && (earliest == null || candidate.isBefore(earliest))) {
          earliest = candidate;
        }
      } catch (Exception ignored) {
      }
    }
    return earliest != null ? earliest : Instant.now();
  }

  private Instant resolveLaunchEndTime() {
    Instant latest = null;
    for (String metricJson : this.metricList) {
      try {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(metricJson);
        Instant candidate = parseSampleInstant(node.path("SampleEndTime").asText(""), null);
        if (candidate != null && (latest == null || candidate.isAfter(latest))) {
          latest = candidate;
        }
      } catch (Exception ignored) {
      }
    }
    return latest != null ? latest : Instant.now();
  }

  private static Instant parseSampleInstant(String raw, Instant fallback) {
    String value = safe(raw);
    if (value.isEmpty()) {
      return fallback;
    }
    try {
      LocalDateTime localDateTime = LocalDateTime.parse(value, SAMPLE_TIME_FORMATTER);
      return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    } catch (DateTimeParseException ex) {
      return fallback;
    }
  }

  private static String normalizeForCodeRef(String value) {
    String s = safe(value).toLowerCase();
    s = s.replaceAll("[^a-z0-9._/-]+", "_");
    return s.isEmpty() ? "unknown" : s;
  }

  private static String buildUniqueId(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      sb.append(safe(p)).append('|');
    }
    return UUID.nameUUIDFromBytes(sb.toString().getBytes()).toString();
  }

  private static String toReportPortalStatus(boolean success, String responseCode, String failureMessage) {
    if (success) {
      return "passed";
    }
    String rc = safe(responseCode);
    String fm = safe(failureMessage).toLowerCase();
    if (fm.contains("skip")) {
      return "skipped";
    }
    if (rc.equals("408") || rc.equals("499")) {
      return "interrupted";
    }
    if (fm.contains("interrupted") || fm.contains("timeout")) {
      return "interrupted";
    }
    if (fm.contains("cancel")) {
      return "cancelled";
    }
    if (fm.contains("abort") || fm.contains("stopped")) {
      return "stopped";
    }
    return "failed";
  }

  private static String buildSuiteSummaryMessage(
      String suiteName,
      int total,
      int passed,
      int failed,
      int skipped,
      int errors) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("<testsuite errors=\"").append(errors).append("\"\n");
    sb.append("           failures=\"").append(failed).append("\"\n");
    sb.append("           name=\"").append(safe(suiteName)).append("\"\n");
    sb.append("           skipped=\"").append(skipped).append("\"\n");
    sb.append("           tests=\"").append(total).append("\"\n");
    sb.append("           passed=\"").append(passed).append("\"/>");
    return sb.toString();
  }

}
