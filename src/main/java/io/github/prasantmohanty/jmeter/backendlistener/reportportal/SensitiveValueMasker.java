package io.github.prasantmohanty.jmeter.backendlistener.reportportal;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SensitiveValueMasker {
  private static final Logger logger = LoggerFactory.getLogger(SensitiveValueMasker.class);
  private static final String TOKEN_MASK_PREFIX = "(?i)(^|[\\r\\n&?{,\\s])(\"?(?:";
  private static final String TOKEN_MASK_SUFFIX =
    ")\"?)(\\s*[:=]\\s*)([^\\r\\n&;,\"]+|\"[^\"]*\")";
  private static final Map<String, MaskingPlan> PLAN_CACHE = new ConcurrentHashMap<>();
  private static final List<String> DEFAULT_MASKED_FIELDS =
    Collections.unmodifiableList(
      Arrays.asList(
        "client_id",
        "client_secret",
        "authorization",
        "password",
        "secret",
        "token",
        "access_token",
        "refresh_token",
        "access_token_g",
        "access_token_g0",
        "access_token_g1",
        "basic_token",
        "api_key"));

  public static final String MASK = "****";

  private SensitiveValueMasker() {}

  public static boolean isMaskedKey(String key, Collection<String> maskedFields) {
    if (key == null) {
      return false;
    }
    String normalizedKey = normalize(key);
    if (normalizedKey.isEmpty()) {
      return false;
    }
    return buildPlan(maskedFields).normalizedFields.contains(normalizedKey);
  }

  public static Object maskValue(String key, Object value, Collection<String> maskedFields) {
    if (!isMaskedKey(key, maskedFields)) {
      return value;
    }
    return MASK;
  }

  public static String maskText(String value, Collection<String> maskedFields) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    MaskingPlan plan = buildPlan(maskedFields);
    if (plan.tokenPattern == null) {
      return value;
    }

    Matcher matcher = plan.tokenPattern.matcher(value);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(
          sb,
          Matcher.quoteReplacement(
              matcher.group(1) + matcher.group(2) + matcher.group(3) + MASK));
    }
    matcher.appendTail(sb);
    logger.debug("Masked text: {}", sb);
    return sb.toString();
  }

  public static List<String> defaultMaskedFields() {
    return DEFAULT_MASKED_FIELDS;
  }

  private static MaskingPlan buildPlan(Collection<String> maskedFields) {
    if (maskedFields == null || maskedFields.isEmpty()) {
      return MaskingPlan.EMPTY;
    }

    LinkedHashMap<String, String> deduplicatedFields = new LinkedHashMap<>();
    for (String maskedField : maskedFields) {
      if (maskedField == null) {
        continue;
      }
      String trimmedField = maskedField.trim();
      if (trimmedField.isEmpty()) {
        continue;
      }
      deduplicatedFields.putIfAbsent(trimmedField.toLowerCase(Locale.ROOT), trimmedField);
    }
    if (deduplicatedFields.isEmpty()) {
      return MaskingPlan.EMPTY;
    }

    StringBuilder cacheKeyBuilder = new StringBuilder();
    for (Map.Entry<String, String> entry : deduplicatedFields.entrySet()) {
      cacheKeyBuilder.append(entry.getKey()).append('\u001F');
    }
    String cacheKey = cacheKeyBuilder.toString();
    return PLAN_CACHE.computeIfAbsent(cacheKey, ignored -> createPlan(deduplicatedFields.values()));
  }

  private static MaskingPlan createPlan(Collection<String> maskedFields) {
    LinkedHashSet<String> normalizedFields = new LinkedHashSet<>();
    StringBuilder patternBuilder = new StringBuilder(TOKEN_MASK_PREFIX);
    boolean hasField = false;
    for (String field : maskedFields) {
      String normalizedField = normalize(field);
      if (normalizedField.isEmpty()) {
        continue;
      }
      normalizedFields.add(normalizedField);
      if (hasField) {
        patternBuilder.append('|');
      }
      patternBuilder.append(Pattern.quote(field));
      hasField = true;
    }
    if (!hasField) {
      return MaskingPlan.EMPTY;
    }
    patternBuilder.append(TOKEN_MASK_SUFFIX);
    return new MaskingPlan(
        Collections.unmodifiableSet(normalizedFields), Pattern.compile(patternBuilder.toString()));
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder normalized = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (Character.isLetterOrDigit(current)) {
        normalized.append(Character.toLowerCase(current));
      }
    }
    return normalized.toString();
  }

  private static final class MaskingPlan {
    private static final MaskingPlan EMPTY =
        new MaskingPlan(Collections.emptySet(), null);

    private final Set<String> normalizedFields;
    private final Pattern tokenPattern;

    private MaskingPlan(Set<String> normalizedFields, Pattern tokenPattern) {
      this.normalizedFields = normalizedFields;
      this.tokenPattern = tokenPattern;
    }
  }
}