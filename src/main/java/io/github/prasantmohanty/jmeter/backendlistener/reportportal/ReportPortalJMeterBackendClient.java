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

import java.util.Arrays;
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

import com.fasterxml.jackson.databind.ser.std.NumberSerializers.LongSerializer;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.google.common.base.Strings;
import com.google.gson.Gson;

import io.github.prasantmohanty.jmeter.backendlistener.model.MetricsRow;

/**
 * A {@link org.apache.jmeter.visualizers.backend.Backend Backend} which produces Report Portal Junit cases
 *
 * @author prasantmohanty
 * @since 20260120
 */
public class ReportPortalJMeterBackendClient extends AbstractBackendListenerClient {

  private static final Logger logger = LoggerFactory.getLogger(ReportPortalJMeterBackendClient.class);

  private static final String BUILD_NUMBER = "BuildNumber";

  private static final String REPORTPORTAL_API_BASE = "ReportPortalAPIBase";

  private static final String REPORTPORTAL_PROJECT_NAME = "ProjectName";

  private static final String REPORTPORTAL_BEARRER_TOKEN_STRING = "BearerToken";

  private static final String REPORTPORTAL_TEST_NAME = "TestName";

  private static final Map<String, String> DEFAULT_ARGS = new LinkedHashMap<>();

  static {

    DEFAULT_ARGS.put(REPORTPORTAL_API_BASE, "http://localhost:8080/api/v1");
    DEFAULT_ARGS.put(REPORTPORTAL_PROJECT_NAME, "MyProject");
    DEFAULT_ARGS.put(REPORTPORTAL_BEARRER_TOKEN_STRING, "my-token");
    DEFAULT_ARGS.put(REPORTPORTAL_TEST_NAME, "JMeter Test");
    DEFAULT_ARGS.put(BUILD_NUMBER, "0");    

  }

  private ReportPortalMetricPublisher publisher;
  private Set<String> modes;
  private Set<String> filters;
  private Set<String> fields;
  private int buildNumber;

  @Override
  public Arguments getDefaultParameters() {
    Arguments arguments = new Arguments();
    DEFAULT_ARGS.forEach(arguments::addArgument);
    return arguments;
  }

  @Override
  public void setupTest(BackendListenerContext context) throws Exception {
    
    logger.debug(BUILD_NUMBER + " parameter: " + context.getParameter(BUILD_NUMBER));
    logger.debug(REPORTPORTAL_API_BASE + " parameter: " + context.getParameter(REPORTPORTAL_API_BASE));
    logger.debug(REPORTPORTAL_PROJECT_NAME + " parameter: " + context.getParameter(REPORTPORTAL_PROJECT_NAME));
    logger.debug(REPORTPORTAL_BEARRER_TOKEN_STRING + " parameter: " + context.getParameter(REPORTPORTAL_BEARRER_TOKEN_STRING));
    logger.debug(REPORTPORTAL_TEST_NAME + " parameter: " + context.getParameter(REPORTPORTAL_TEST_NAME));
    
    Map<String, String> reportPortalConfigs = new HashMap<>();
    reportPortalConfigs.put(REPORTPORTAL_API_BASE, context.getParameter(REPORTPORTAL_API_BASE));
    reportPortalConfigs.put(REPORTPORTAL_PROJECT_NAME, context.getParameter(REPORTPORTAL_PROJECT_NAME));
    reportPortalConfigs.put(REPORTPORTAL_BEARRER_TOKEN_STRING, context.getParameter(REPORTPORTAL_BEARRER_TOKEN_STRING));
    reportPortalConfigs.put(REPORTPORTAL_TEST_NAME, context.getParameter(REPORTPORTAL_TEST_NAME));
    reportPortalConfigs.put(BUILD_NUMBER, context.getParameter(BUILD_NUMBER));

    this.filters = new HashSet<>();
    this.fields = new HashSet<>();
    this.modes = new HashSet<>(Arrays.asList("info", "debug", "error", "quiet"));
    this.buildNumber =
        (JMeterUtils.getProperty(ReportPortalJMeterBackendClient.BUILD_NUMBER) != null
                && !JMeterUtils.getProperty(ReportPortalJMeterBackendClient.BUILD_NUMBER).trim().equals(""))
            ? Integer.parseInt(JMeterUtils.getProperty(ReportPortalJMeterBackendClient.BUILD_NUMBER))
            : 0;

    Properties props = new Properties();
  
    //convertParameterToSet(context, KAFKA_SAMPLE_FILTER, this.filters);
    //convertParameterToSet(context, KAFKA_FIELDS, this.fields);
    //KafkaProducer<Long, String> producer = new KafkaProducer<>(props);

    this.publisher = new ReportPortalMetricPublisher(reportPortalConfigs);

    //checkTestMode(context.getParameter(KAFKA_TEST_MODE));
    super.setupTest(context);
  }

  /** This method  converts a semicolon separated list contained in a parameter into a string set */
  private void convertParameterToSet(BackendListenerContext context, String parameter, Set<String> set) {
    String[] array =
        (context.getParameter(parameter).contains(";"))
            ? context.getParameter(parameter).split(";")
            : new String[] {context.getParameter(parameter)};
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
    
      MetricsRow row =
          new MetricsRow(
              sr,
              //context.getParameter(KAFKA_TEST_MODE),
              //context.getParameter(KAFKA_TIMESTAMP),
              this.buildNumber,               
              fields);
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
              "The Report Portal Backend Listener was unable to add sampler to the list of samplers to send... More info in JMeter's console.");
          e.printStackTrace();
        }
      }
    }

    try {
      logger.debug("Publishing " + this.publisher.getListSize() + " metrics to report portal.");
      this.publisher.publishMetrics();
    } catch (Exception e) {
      logger.error("Error occurred while publishing to report portal.", e);
    } finally {
      logger.debug("Clearing report portal metrics list.");
      this.publisher.clearList();
    }
  }

  @Override
  public void teardownTest(BackendListenerContext context) throws Exception {
    if (this.publisher.getListSize() > 0) {
      this.publisher.publishMetrics();
    }
    //this.publisher.closeProducer();
    super.teardownTest(context);
  }

  /**
   * This method checks if the test mode is valid
   *
   * @param mode The test mode as String
   */
 /*  private void checkTestMode(String mode) {
    if (!this.modes.contains(mode)) {
      logger.warn(
          "The parameter \"kafka.test.mode\" isn't set properly. Three modes are allowed: debug ,info, and quiet.");
      logger.warn(
          " -- \"debug\": sends request and response details to Kafka. Info only sends the details if the response has an error.");
      logger.warn(" -- \"info\": should be used in production");
      logger.warn(" -- \"error\": should be used if you.");
      logger.warn(" -- \"quiet\": should be used if you don't care to have the details.");
    }
  } */

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

    /* // if sample is successful but test mode is "error" only
    if (sr.isSuccessful()
        && context.getParameter(KAFKA_TEST_MODE).trim().equalsIgnoreCase("error")
        && valid) {
      valid = false;
    } */
    logger.debug("Sample validation result: " + valid);
    return valid;
  }
}
