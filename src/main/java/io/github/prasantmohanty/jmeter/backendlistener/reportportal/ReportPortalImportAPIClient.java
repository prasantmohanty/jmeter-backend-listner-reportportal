package io.github.prasantmohanty.jmeter.backendlistener.reportportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.prasantmohanty.jmeter.backendlistener.model.LaunchImportRq;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Map;


public class ReportPortalImportAPIClient {
  private final HttpUrl apiBase;           // e.g., https://rp.example.com/api
  private final String projectName;        // e.g., "my_project"
  private final String bearerToken;        // JWT or API Key
  private final String testName;         // e.g., "JMeter Test"
  private final String buildNumber;      // e.g., "123"

  private final OkHttpClient http;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                                         .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ReportPortalImportAPIClient.class);

  public ReportPortalImportAPIClient(Map<String, String> reportPortalConfigs) {

    Objects.requireNonNull(reportPortalConfigs.get("ReportPortalAPIBase"), "apiBaseUrl");
    Objects.requireNonNull(reportPortalConfigs.get("ProjectName"), "projectName");
    Objects.requireNonNull(reportPortalConfigs.get("BearerToken"), "bearerToken");
    Objects.requireNonNull(reportPortalConfigs.get("TestName"), "testName");
    Objects.requireNonNull(reportPortalConfigs.get("BuildNumber"), "buildNumber");

    this.apiBase = HttpUrl.parse(reportPortalConfigs.get("ReportPortalAPIBase"));
    if (this.apiBase == null) throw new IllegalArgumentException("Invalid apiBaseUrl");
  
    this.projectName = reportPortalConfigs.get("ProjectName");
    this.bearerToken = reportPortalConfigs.get("BearerToken");
    this.testName = reportPortalConfigs.get("TestName");
    this.buildNumber = reportPortalConfigs.get("BuildNumber");
    
    logger.debug("Initialized ReportPortalImportAPIClient for project: " + this.projectName + ", testName: " + this.testName + ", build: " + this.buildNumber);

    this.http = new OkHttpClient();
  }

  public String importLaunch(File junitXmlOrZip, LaunchImportRq rq) throws IOException {
    if (junitXmlOrZip == null || !junitXmlOrZip.exists()) {
      throw new IllegalArgumentException("Input file does not exist: " + junitXmlOrZip);
    }
    String contentType = guessContentType(junitXmlOrZip.getName());
    String rqJson = mapper.writeValueAsString(rq);

    MediaType fileMedia = MediaType.parse(contentType);
    RequestBody fileBody = RequestBody.create(junitXmlOrZip, fileMedia);

    RequestBody jsonPart = RequestBody.create(rqJson, MediaType.parse("application/json"));

    MultipartBody multipart = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", junitXmlOrZip.getName(), fileBody)
        .addFormDataPart("launchImportRq", null, jsonPart)
        .build();

    HttpUrl url = apiBase
        .newBuilder()
        .addPathSegments("v1/plugin/" + projectName + "/junit/import")
        .build();

    Request req = new Request.Builder()
        .url(url)
        .post(multipart)
        .addHeader("Authorization", "Bearer " + bearerToken)
        .addHeader("Accept", "application/json")
        .build();

    try (Response resp = http.newCall(req).execute()) {
      if (!resp.isSuccessful()) {
        String body = (resp.body() != null) ? resp.body().string() : "";
        throw new IOException("Import failed: HTTP " + resp.code() + " - " + body);
      }
      return (resp.body() != null) ? resp.body().string() : "";
    }
  }

  private static String guessContentType(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".xml")) return "text/xml";
    if (lower.endsWith(".zip")) return "application/zip";
    // Fallback
    return "application/octet-stream";
  }

  /** Simple health check to verify token works against base. */
  public boolean ping() {
    try {
      // “GET /v1” is not a standard health endpoint; adapt to your environment if you have one.
      Request req = new Request.Builder()
          .url(apiBase.newBuilder().addPathSegment("v1").build())
          .addHeader("Authorization", "Bearer " + bearerToken)
          .build();
      try (Response resp = http.newCall(req).execute()) {
        return resp.code() < 500;
      }
    } catch (Exception e) {
      return false;
    }
  }
}
