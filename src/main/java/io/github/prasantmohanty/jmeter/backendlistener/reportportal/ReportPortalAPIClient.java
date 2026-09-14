package io.github.prasantmohanty.jmeter.backendlistener.reportportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.prasantmohanty.jmeter.backendlistener.model.Attribute;
import io.github.prasantmohanty.jmeter.backendlistener.model.LaunchImportRq;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal ReportPortal v2 API client used by the publisher.
 * <p>This client implements only the small subset of endpoints required: start launch, create
 * test item, send log, and finish test item. It is intentionally small and defensive so tests
 * and compilation remain stable.
 */
public class ReportPortalAPIClient {

  private static final Logger logger = LoggerFactory.getLogger(ReportPortalAPIClient.class);

  private static final int DEFAULT_HTTP_TIMEOUT_MS = 180000;
  private static final int LOG_MAX_RETRIES = 3;

  public static class ItemParameter {
    private final String key;
    private final String value;

    public ItemParameter(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }

  private final HttpUrl apiBase;
  private final String projectName;
  private final String bearerToken;
  private final OkHttpClient http;
  private final ObjectMapper mapper;
  private final int callTimeoutMs;
  private final int connectTimeoutMs;
  private final int writeTimeoutMs;
  private final int readTimeoutMs;

  public ReportPortalAPIClient(Map<String, String> reportPortalConfigs) {
    Objects.requireNonNull(reportPortalConfigs.get("ReportPortalAPIBase"), "apiBaseUrl");
    Objects.requireNonNull(reportPortalConfigs.get("ProjectName"), "projectName");
    Objects.requireNonNull(reportPortalConfigs.get("BearerToken"), "bearerToken");

    this.apiBase = HttpUrl.parse(reportPortalConfigs.get("ReportPortalAPIBase"));
    if (this.apiBase == null) throw new IllegalArgumentException("Invalid apiBaseUrl");

    this.projectName = reportPortalConfigs.get("ProjectName");
    this.bearerToken = reportPortalConfigs.get("BearerToken");
    this.callTimeoutMs =
      parseTimeoutMs(reportPortalConfigs, "HttpTimeoutMs", DEFAULT_HTTP_TIMEOUT_MS);
    this.connectTimeoutMs =
      parseTimeoutMs(reportPortalConfigs, "HttpConnectTimeoutMs", this.callTimeoutMs);
    this.writeTimeoutMs =
      parseTimeoutMs(reportPortalConfigs, "HttpWriteTimeoutMs", this.callTimeoutMs);
    this.readTimeoutMs = parseTimeoutMs(reportPortalConfigs, "HttpReadTimeoutMs", this.callTimeoutMs);

    this.http =
        new OkHttpClient.Builder()
        .connectTimeout(this.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(this.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(this.readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(this.callTimeoutMs, TimeUnit.MILLISECONDS)
            .build();

    logger.debug(
        "Configured HTTP timeouts (ms): call={}, connect={}, write={}, read={}",
        this.callTimeoutMs,
        this.connectTimeoutMs,
        this.writeTimeoutMs,
        this.readTimeoutMs);

    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  private static int parseTimeoutMs(
      Map<String, String> reportPortalConfigs, String key, int defaultValue) {
    String raw = reportPortalConfigs.get(key);
    if (raw == null || raw.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      if (parsed <= 0) {
        logger.warn("{} must be > 0. Using default {} ms.", key, defaultValue);
        return defaultValue;
      }
      return parsed;
    } catch (NumberFormatException ex) {
      logger.warn("{} is not a valid integer (value='{}'). Using default {} ms.", key, raw, defaultValue);
      return defaultValue;
    }
  }

  /** Start a launch using POST /api/v2/{project}/launch. Returns id/uuid or raw response. */
  public String startLaunch(LaunchImportRq rq) throws IOException {
    ObjectNode body = mapper.createObjectNode();
    if (rq.getName() != null) body.put("name", rq.getName());
    if (rq.getDescription() != null) body.put("description", rq.getDescription());
    if (rq.getMode() != null) body.put("mode", rq.getMode());
    if (rq.getStartTime() != null) body.put("startTime", rq.getStartTime().toString());

    if (rq.getAttributes() != null && !rq.getAttributes().isEmpty()) {
      ArrayNode attrs = body.putArray("attributes");
      for (Attribute a : rq.getAttributes()) {
        ObjectNode an = mapper.createObjectNode();
        if (a.getKey() != null) an.put("key", a.getKey());
        if (a.getValue() != null) an.put("value", a.getValue());
        if (a.getSystem() != null) an.put("system", a.getSystem());
        attrs.add(an);
      }
    }

    String json = mapper.writeValueAsString(body);
    RequestBody rb = RequestBody.create(json, MediaType.parse("application/json"));
    HttpUrl url = apiBase.newBuilder().addPathSegments("v2/" + projectName + "/launch").build();
    logger.debug("startLaunch: url={}", url);
    logger.debug(
      "startLaunch: timeouts(ms) call={}, connect={}, write={}, read={}",
      callTimeoutMs,
      connectTimeoutMs,
      writeTimeoutMs,
      readTimeoutMs);
    logger.debug("bearerToken: {}", bearerToken);
    logger.debug("startLaunch: request body={}", json);

    logger.debug("startLaunch: building request...");
    Request req =
        new Request.Builder()
            .url(url)
            .post(rb)
            .addHeader("Authorization", "Bearer " + bearerToken)
            .addHeader("Accept", "application/json")
            .build();
    logger.debug("startLaunch: request built, executing...");

    try (Response resp = http.newCall(req).execute()) {
      if (!resp.isSuccessful()) {
        String b = (resp.body() != null) ? resp.body().string() : "";
        throw new IOException("startLaunch failed: HTTP " + resp.code() + " - " + b);
      }
      String respBody = (resp.body() != null) ? resp.body().string() : "";
      logger.debug("startLaunch: response body={}", respBody);
      try {
        JsonNode node = mapper.readTree(respBody);
        JsonNode uuidNode = node.findValue("uuid");
        if (uuidNode != null && !uuidNode.isNull()) {
          String uuid = uuidNode.asText();
          if (uuid != null && !uuid.trim().isEmpty()) {
            return uuid;
          }
        }
        JsonNode idNode = node.findValue("id");
        if (idNode != null && !idNode.isNull()) {
          String id = idNode.asText();
          if (id != null && !id.trim().isEmpty()) {
            return id;
          }
        }
      } catch (Exception e) {
        logger.debug("startLaunch: unable to parse response as JSON", e);
      }
      throw new IOException(
          "startLaunch succeeded but response did not contain a usable launch uuid/id: " + respBody);
    }
  }

  /** Create a root test item using POST /api/v2/{project}/item. Returns item id/uuid. */
  public String startTestItem(
      String launchUuid,
      String name,
      String description,
      String type,
      Instant startTime,
      String codeRef,
      String uniqueId,
      List<Attribute> attributes,
      List<ItemParameter> parameters)
      throws IOException {
    return startItemInternal(
        null,
        launchUuid,
        name,
        description,
        type,
        startTime,
        codeRef,
        uniqueId,
        attributes,
        parameters);
  }

  /** Create a child test item using POST /api/v2/{project}/item/{parentItemUuid}. */
  public String startChildTestItem(
      String parentItemUuid,
      String launchUuid,
      String name,
      String description,
      String type,
      Instant startTime,
      String codeRef,
      String uniqueId,
      List<Attribute> attributes,
      List<ItemParameter> parameters)
      throws IOException {
    return startItemInternal(
        parentItemUuid,
        launchUuid,
        name,
        description,
        type,
        startTime,
        codeRef,
        uniqueId,
        attributes,
        parameters);
  }

  private String startItemInternal(
      String parentItemUuid,
      String launchUuid,
      String name,
      String description,
      String type,
      Instant startTime,
      String codeRef,
      String uniqueId,
      List<Attribute> attributes,
      List<ItemParameter> parameters)
      throws IOException {
    ObjectNode body = mapper.createObjectNode();
    if (name != null) body.put("name", name);
    if (description != null) body.put("description", description);
    if (launchUuid != null) body.put("launchUuid", launchUuid);
    if (type != null) body.put("type", normalizeItemType(type));
    body.put("hasStats", true);
    if (startTime != null) body.put("startTime", startTime.toString());
    if (codeRef != null) body.put("codeRef", codeRef);
    if (uniqueId != null) body.put("uniqueId", uniqueId);

    List<Attribute> safeAttributes = (attributes == null) ? Collections.emptyList() : attributes;
    if (!safeAttributes.isEmpty()) {
      ArrayNode attrs = body.putArray("attributes");
      for (Attribute a : safeAttributes) {
        ObjectNode an = mapper.createObjectNode();
        if (a.getKey() != null) an.put("key", a.getKey());
        if (a.getValue() != null) an.put("value", a.getValue());
        if (a.getSystem() != null) an.put("system", a.getSystem());
        attrs.add(an);
      }
    }

    List<ItemParameter> safeParameters =
        (parameters == null) ? Collections.emptyList() : parameters;
    if (!safeParameters.isEmpty()) {
      ArrayNode params = body.putArray("parameters");
      for (ItemParameter p : safeParameters) {
        ObjectNode pn = mapper.createObjectNode();
        if (p.getKey() != null) pn.put("key", p.getKey());
        if (p.getValue() != null) pn.put("value", p.getValue());
        params.add(pn);
      }
    }

    String json = mapper.writeValueAsString(body);
    RequestBody rb = RequestBody.create(json, MediaType.parse("application/json"));
    HttpUrl.Builder urlBuilder = apiBase.newBuilder().addPathSegments("v2/" + projectName + "/item");
    if (parentItemUuid != null && !parentItemUuid.trim().isEmpty()) {
      urlBuilder.addPathSegment(parentItemUuid);
    }
    HttpUrl url = urlBuilder.build();
    Request req =
        new Request.Builder()
            .url(url)
            .post(rb)
            .addHeader("Authorization", "Bearer " + bearerToken)
            .addHeader("Accept", "application/json")
            .build();

    try (Response resp = http.newCall(req).execute()) {
      if (!resp.isSuccessful()) {
        String b = (resp.body() != null) ? resp.body().string() : "";
        throw new IOException("startTestItem failed: HTTP " + resp.code() + " - " + b);
      }
      String respBody = (resp.body() != null) ? resp.body().string() : "";
      try {
        JsonNode node = mapper.readTree(respBody);
        JsonNode uuidNode = node.findValue("uuid");
        if (uuidNode != null && !uuidNode.isNull()) {
          String uuid = uuidNode.asText();
          if (uuid != null && !uuid.trim().isEmpty()) {
            return uuid;
          }
        }
        JsonNode idNode = node.findValue("id");
        if (idNode != null && !idNode.isNull()) {
          String id = idNode.asText();
          if (id != null && !id.trim().isEmpty()) {
            return id;
          }
        }
      } catch (Exception e) {
        logger.debug("startTestItem: unable to parse response as JSON", e);
      }
      throw new IOException(
          "startTestItem succeeded but response did not contain a usable item uuid/id: " + respBody);
    }
  }

  private static String normalizeItemType(String type) {
    if (type == null) {
      return null;
    }
    String normalized = type.trim();
    if (normalized.isEmpty()) {
      return normalized;
    }
    return normalized.toUpperCase(Locale.ROOT);
  }

  /** Send a log entry using POST /api/v2/{project}/log. */
  public void log(String launchUuid, String itemUuid, String level, String message, Instant time)
      throws IOException {
    IOException lastException = null;
    for (int attempt = 1; attempt <= LOG_MAX_RETRIES; attempt++) {
      try {
        sendLogOnce(launchUuid, itemUuid, level, message, time);
        return;
      } catch (IOException ex) {
        lastException = ex;
        boolean retryable = isRetryableLogError(ex);
        if (!retryable || attempt == LOG_MAX_RETRIES) {
          throw ex;
        }
        logger.warn(
            "log request failed (attempt {}/{}). Retrying. Cause: {}",
            attempt,
            LOG_MAX_RETRIES,
            ex.getMessage());
      }
    }
    if (lastException != null) {
      throw lastException;
    }
  }

  private void sendLogOnce(
      String launchUuid, String itemUuid, String level, String message, Instant time)
      throws IOException {
    ObjectNode body = mapper.createObjectNode();
    if (launchUuid != null) body.put("launchUuid", launchUuid);
    if (itemUuid != null) body.put("itemUuid", itemUuid);
    if (level != null) body.put("level", level);
    if (message != null) body.put("message", message);
    if (time != null) body.put("time", time.toString());

    String json = mapper.writeValueAsString(body);
    RequestBody rb = RequestBody.create(json, MediaType.parse("application/json"));
    HttpUrl url = apiBase.newBuilder().addPathSegments("v2/" + projectName + "/log").build();
    Request req =
        new Request.Builder()
            .url(url)
            .post(rb)
            .addHeader("Authorization", "Bearer " + bearerToken)
            .addHeader("Accept", "application/json")
            .build();

    try (Response resp = http.newCall(req).execute()) {
      if (!resp.isSuccessful()) {
        String b = (resp.body() != null) ? resp.body().string() : "";
        throw new IOException("log failed: HTTP " + resp.code() + " - " + b);
      }
    }
  }

  private static boolean isRetryableLogError(IOException ex) {
    String msg = ex.getMessage();
    if (msg == null) {
      return false;
    }
    return msg.contains("HTTP 502")
        || msg.contains("HTTP 503")
        || msg.contains("HTTP 504")
        || msg.contains("HTTP 429")
        || msg.contains("timed out")
        || msg.contains("timeout");
  }

  /** Finish an item using PUT /api/v2/{project}/item/{itemUuid}. */
  public void finishTestItem(String launchUuid, String itemUuid, String status, Instant endTime)
      throws IOException {
    ObjectNode body = mapper.createObjectNode();
    if (launchUuid != null) body.put("launchUuid", launchUuid);
    if (status != null) body.put("status", status);
    if (endTime != null) body.put("endTime", endTime.toString());

    String json = mapper.writeValueAsString(body);
    RequestBody rb = RequestBody.create(json, MediaType.parse("application/json"));
    HttpUrl url = apiBase.newBuilder().addPathSegments("v2/" + projectName + "/item/" + itemUuid).build();
    logger.debug("finishTestItem: url={}, json={}", url, json);
    Request req =
        new Request.Builder()
            .url(url)
            .put(rb)
            .addHeader("Authorization", "Bearer " + bearerToken)
            .addHeader("Accept", "application/json")
            .build();

    try (Response resp = http.newCall(req).execute()) {
      String respBody = (resp.body() != null) ? resp.body().string() : "";
      logger.debug(
          "finishTestItem: response code={}, message={}, body={}",
          resp.code(),
          resp.message(),
          respBody);
      if (!resp.isSuccessful()) {
        throw new IOException("finishTestItem failed: HTTP " + resp.code() + " - " + respBody);
      }

      // Detect proxy/gateway success pages that are not ReportPortal API responses.
      String trimmed = respBody == null ? "" : respBody.trim();
      if (!trimmed.isEmpty() && trimmed.startsWith("<")) {
        throw new IOException(
            "finishTestItem returned non-JSON body (possible proxy response), status may not be persisted: "
                + trimmed);
      }
    }
  }

  /** Finish launch using PUT /api/v2/{project}/launch/{launchUuid}/finish. */
  public void finishLaunch(String launchUuid, String status, Instant endTime) throws IOException {
    ObjectNode body = mapper.createObjectNode();
    if (endTime != null) body.put("endTime", endTime.toString());
    if (status != null) body.put("status", status);

    String json = mapper.writeValueAsString(body);
    RequestBody rb = RequestBody.create(json, MediaType.parse("application/json"));
    HttpUrl url =
        apiBase
            .newBuilder()
            .addPathSegments("v2/" + projectName + "/launch/" + launchUuid + "/finish")
            .build();
    Request req =
        new Request.Builder()
            .url(url)
            .put(rb)
            .addHeader("Authorization", "Bearer " + bearerToken)
            .addHeader("Accept", "application/json")
            .build();

    try (Response resp = http.newCall(req).execute()) {
      if (!resp.isSuccessful()) {
        String b = (resp.body() != null) ? resp.body().string() : "";
        throw new IOException("finishLaunch failed: HTTP " + resp.code() + " - " + b);
      }
    }
  }
}
