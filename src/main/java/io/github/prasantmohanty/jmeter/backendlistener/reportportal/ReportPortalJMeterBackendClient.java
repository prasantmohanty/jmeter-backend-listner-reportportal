/*
 * Copyright 2026 Prasant Mohanty
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

import com.google.gson.Gson;
import io.github.prasantmohanty.jmeter.backendlistener.model.MetricsRow;
import io.github.prasantmohanty.jmeter.backendlistener.reportportal.SensitiveValueMasker;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.jmeter.visualizers.backend.Backend Backend} which produces Report Portal
 * Junit cases
 *
 * @author prasantmohanty
 * @since 20260120
 */
public class ReportPortalJMeterBackendClient extends AbstractBackendListenerClient {

  private static final Logger logger =
      LoggerFactory.getLogger(ReportPortalJMeterBackendClient.class);

  private static final String BUILD_NUMBER = "BuildNumber";

  private static final String REPORTPORTAL_API_BASE = "ReportPortalAPIBase";

  private static final String REPORTPORTAL_PROJECT_NAME = "ProjectName";

  private static final String REPORTPORTAL_BEARRER_TOKEN_STRING = "BearerToken";

  private static final String REPORTPORTAL_TEST_NAME = "TestName";

  private static final String REPORTPORTAL_HTTP_TIMEOUT_MS = "HttpTimeoutMs";

  private static final String REPORTPORTAL_HTTP_CONNECT_TIMEOUT_MS = "HttpConnectTimeoutMs";

  private static final String REPORTPORTAL_HTTP_WRITE_TIMEOUT_MS = "HttpWriteTimeoutMs";

  private static final String REPORTPORTAL_HTTP_READ_TIMEOUT_MS = "HttpReadTimeoutMs";

  private static final String REPORTPORTAL_PROPERTIES_PATH = "ReportPortalPropertiesPath";

  private static final String REPORTPORTAL_FILTERS = "Filters";

  private static final String REPORTPORTAL_FIELDS = "Fields";

  private static final String REPORTPORTAL_MASKED_FIELDS = "MaskedFields";

  private static final Map<String, String> DEFAULT_ARGS = new LinkedHashMap<>();

  static {
    // IMPORTANT: ReportPortalAPIBase should be the base URL without /v1
    // Correct:   http://reportportal.example.com/api
    // Wrong:     http://reportportal.example.com/api/v1  (causes double v1 in path)
    
    /**DEFAULT_ARGS.put(REPORTPORTAL_API_BASE, "http://localhost:8080/api");
    DEFAULT_ARGS.put(REPORTPORTAL_PROJECT_NAME, "MyProject");
    DEFAULT_ARGS.put(REPORTPORTAL_BEARRER_TOKEN_STRING, "my-token");
    DEFAULT_ARGS.put(REPORTPORTAL_TEST_NAME, "JMeter Test");
    DEFAULT_ARGS.put(REPORTPORTAL_HTTP_TIMEOUT_MS, "180000");
    DEFAULT_ARGS.put(REPORTPORTAL_HTTP_CONNECT_TIMEOUT_MS, "180000");
    DEFAULT_ARGS.put(REPORTPORTAL_HTTP_WRITE_TIMEOUT_MS, "180000");
    DEFAULT_ARGS.put(REPORTPORTAL_HTTP_READ_TIMEOUT_MS, "180000");
    DEFAULT_ARGS.put(BUILD_NUMBER, "0");
    **/
    DEFAULT_ARGS.put(REPORTPORTAL_PROPERTIES_PATH, resolveDefaultPropertiesPath());
  }

  private ReportPortalMetricPublisher publisher;
  private Set<String> filters;
  private Set<String> fields;
  private List<String> maskedFields;
  private String buildNumber;
  private String testName;

  @Override
  public Arguments getDefaultParameters() {
    Arguments arguments = new Arguments();
    DEFAULT_ARGS.forEach(arguments::addArgument);
    return arguments;
  }

  /**
   * Prepare default backend listener arguments used by JMeter GUI.
   *
   * @return Arguments populated with default values
   */
  @Override
  public void setupTest(BackendListenerContext context) throws Exception {
    String propertiesPath = resolvePropertiesPath(context);
    Properties reportPortalProperties = loadReportPortalProperties(propertiesPath);

    logger.debug("Loading ReportPortal configuration from: {}", propertiesPath);

    Map<String, String> reportPortalConfigs = new HashMap<>();
    reportPortalConfigs.put(
      REPORTPORTAL_API_BASE,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_API_BASE));
    reportPortalConfigs.put(
      REPORTPORTAL_PROJECT_NAME,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_PROJECT_NAME));
    reportPortalConfigs.put(
      REPORTPORTAL_BEARRER_TOKEN_STRING,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_BEARRER_TOKEN_STRING));
    reportPortalConfigs.put(
      REPORTPORTAL_TEST_NAME,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_TEST_NAME));
    reportPortalConfigs.put(
      REPORTPORTAL_HTTP_TIMEOUT_MS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_HTTP_TIMEOUT_MS));
    reportPortalConfigs.put(
      REPORTPORTAL_HTTP_CONNECT_TIMEOUT_MS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_HTTP_CONNECT_TIMEOUT_MS));
    reportPortalConfigs.put(
      REPORTPORTAL_HTTP_WRITE_TIMEOUT_MS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_HTTP_WRITE_TIMEOUT_MS));
    reportPortalConfigs.put(
      REPORTPORTAL_HTTP_READ_TIMEOUT_MS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_HTTP_READ_TIMEOUT_MS));
    reportPortalConfigs.put(BUILD_NUMBER, resolveConfigValue(reportPortalProperties, context, BUILD_NUMBER));
    reportPortalConfigs.put(
      REPORTPORTAL_FILTERS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_FILTERS));
    reportPortalConfigs.put(
      REPORTPORTAL_FIELDS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_FIELDS));
    reportPortalConfigs.put(
      REPORTPORTAL_MASKED_FIELDS,
      resolveConfigValue(reportPortalProperties, context, REPORTPORTAL_MASKED_FIELDS));

    // Validate required configuration
    String apiBase = reportPortalConfigs.get(REPORTPORTAL_API_BASE);
    if (apiBase == null || apiBase.trim().isEmpty()) {
      throw new IllegalArgumentException("ReportPortalAPIBase is required but not configured");
    }
    if (apiBase.endsWith("/v1")) {
      logger.warn("WARNING: ReportPortalAPIBase ends with /v1 which will cause incorrect URL paths.");
      logger.warn("Remove /v1 from the end of ReportPortalAPIBase (e.g., use http://server/api instead of http://server/api/v1)");
    }

    this.filters = new HashSet<>();
    this.fields = new HashSet<>();
    Set<String> maskedFieldsSet = new HashSet<>();
    convertParameterToSet(reportPortalConfigs.get(REPORTPORTAL_FILTERS), this.filters);
    convertParameterToSet(reportPortalConfigs.get(REPORTPORTAL_FIELDS), this.fields);
    maskedFieldsSet.addAll(SensitiveValueMasker.defaultMaskedFields());
    convertParameterToSet(reportPortalConfigs.get(REPORTPORTAL_MASKED_FIELDS), maskedFieldsSet);
    this.maskedFields = new java.util.ArrayList<>(maskedFieldsSet);
    this.buildNumber = normalizeOrDefault(reportPortalConfigs.get(BUILD_NUMBER), "0");
    this.testName = normalizeOrDefault(reportPortalConfigs.get(REPORTPORTAL_TEST_NAME), "JMeter Test");
    logger.debug("Build Number: " + this.buildNumber);
    logger.debug("Test Name: " + this.testName);

    this.publisher = new ReportPortalMetricPublisher(reportPortalConfigs);

    super.setupTest(context);
  }

  /**
   * Initialize the backend when the test starts. This sets up local state and publisher.
   *
   * @param context Backend listener context supplied by JMeter
   * @throws Exception if initialization fails
   */

  /**
   * Convert a semicolon separated parameter value into a set of lowercase strings.
   *
   * @param parameter semicolon-delimited values
   * @param set destination set to populate
   */
  private void convertParameterToSet(String parameter, Set<String> set) {
    if (parameter == null || parameter.trim().isEmpty()) {
      return;
    }
    String[] array = parameter.contains(";") ? parameter.split(";") : new String[] {parameter};
    if (array.length > 0 && !array[0].trim().equals("")) {
      for (String entry : array) {
        set.add(entry.toLowerCase().trim());
        if (logger.isDebugEnabled()) {
          logger.debug("Parsed from " + parameter + ": " + entry.toLowerCase().trim());
        }
      }
    }
  }

  @Override
  public void handleSampleResults(List<SampleResult> results, BackendListenerContext context) {
    for (SampleResult sr : results) {

      MetricsRow row = new MetricsRow(sr, this.buildNumber, fields, this.maskedFields);

      logger.debug("Generated MetricsRow: " + row.toString());

      if (validateSample(context, sr)) {
        try {
          // Prefix to skip from adding service specific parameters to the metrics row
          String servicePrefixName = "reportPortal.";
          String gson = new Gson().toJson(row.getRowAsMap(context, servicePrefixName));
          logger.debug("Adding to report portal list: " + gson);
          this.publisher.addToList(gson);
        } catch (Exception e) {
          logger.error(
              "The Report Portal Backend Listener was unable to add sampler to the list of samplers"
                  + " to send... More info in JMeter's console.");
          e.printStackTrace();
        }
      }
    }

    try {
      // Do not publish on every sample batch. Collect metrics for the whole test and
      // publish them once on teardown to avoid creating multiple launches in ReportPortal.
      logger.debug(
          "Collected "
              + this.publisher.getListSize()
              + " metrics (deferring publish until teardown).");
    } catch (Exception e) {
      logger.error("Error occurred while publishing to report portal.", e);
    } finally {
      // Do not clear here; keep accumulated metrics until teardownTest triggers the single import.
    }
  }

  /**
   * Process a batch of sample results produced by JMeter during the test run. Each sample is
   * converted into a MetricsRow and queued for publishing.
   *
   * @param results list of SampleResult objects
   * @param context Backend listener context
   */
  @Override
  public void teardownTest(BackendListenerContext context) throws Exception {
    if (this.publisher.getListSize() > 0) {
      logger.debug(
          "Publishing accumulated "
              + this.publisher.getListSize()
              + " metrics to ReportPortal at teardown.");
      this.publisher.publishMetrics();
      // clear after publishing so repeated runs or multiple teardown calls don't resend the same
      // data
      this.publisher.clearList();
      logger.debug("Cleared accumulated metrics after publish.");
    }
    // this.publisher.closeProducer();
    super.teardownTest(context);
  }

  /**
   * This method checks if the test mode is valid
   *
   * @param mode The test mode as String
   */

  /**
   * This method will validate the current sample to see if it is part of the filters or not.
   *
   * @param context The Backend Listener's context
   * @param sr The current SampleResult
   * @return true or false depending on whether or not the sample is valid
   */
  private boolean validateSample(BackendListenerContext context, SampleResult sr) {
    boolean valid = true;
    String sampleLabel = sr.getSampleLabel().toLowerCase().trim();
    logger.debug("Validating sample label: " + sampleLabel);
    if (this.filters.size() > 0) {
      for (String filter : filters) {
        Pattern pattern = Pattern.compile(filter);
        Matcher matcher = pattern.matcher(sampleLabel);

        if (sampleLabel.contains(filter) || matcher.find()) {
          valid = true;
          break;
        } else {
          valid = false;
        }
      }
    }

    logger.debug("Sample validation result: " + valid);
    return valid;
  }

  private static String resolveDefaultPropertiesPath() {
    String binDir = JMeterUtils.getJMeterBinDir();
    if (binDir == null || binDir.trim().isEmpty()) {
      binDir = System.getProperty("user.dir", ".");
    }
    logger.debug("Resolving default properties path from JMeter bin directory: " + binDir);
    return new File(binDir, "reportportal.properties").getAbsolutePath();
  }

  private String resolvePropertiesPath(BackendListenerContext context) {
    String configuredPath = context.getParameter(REPORTPORTAL_PROPERTIES_PATH);
    if (configuredPath != null && !configuredPath.trim().isEmpty()) {
      return configuredPath.trim();
    }
    return resolveDefaultPropertiesPath();
  }

  private Properties loadReportPortalProperties(String propertiesPath) throws IOException {
    File propertiesFile = new File(propertiesPath);
    if (!propertiesFile.exists()) {
      throw new IllegalArgumentException(
          "ReportPortal properties file does not exist: " + propertiesFile.getAbsolutePath());
    }

    Properties properties = new Properties();
    try (FileInputStream inputStream = new FileInputStream(propertiesFile)) {
      properties.load(inputStream);
    }
    return properties;
  }

  private String resolveConfigValue(
      Properties properties, BackendListenerContext context, String key) {
    String fileValue = normalizeOrNull(properties.getProperty(key));
    if (fileValue != null) {
      return fileValue;
    }

    String contextValue = normalizeOrNull(context.getParameter(key));
    if (contextValue != null) {
      return contextValue;
    }

    return null;
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    String normalized = normalizeOrNull(value);
    return normalized != null ? normalized : defaultValue;
  }

  private static String normalizeOrNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
