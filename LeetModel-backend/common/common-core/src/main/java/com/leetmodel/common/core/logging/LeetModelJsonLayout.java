package com.leetmodel.common.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.event.KeyValuePair;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LeetModel 统一 JSON 日志布局。
 *
 * <p>只输出版本化白名单字段，不透传任意 MDC 或 SLF4J key-value，避免请求输入改变
 * 日志资源身份或无限扩展 schema。异常栈只保留代码位置，不包含可能携带敏感正文的
 * Throwable message；所有字符串在最终 JSON 编码前经过不可逆脱敏、控制字符清理和限长。</p>
 */
public final class LeetModelJsonLayout extends LayoutBase<ILoggingEvent> {

    public static final String SCHEMA_VERSION = "leetmodel.log.v1";
    public static final String UNCLASSIFIED_EVENT = LogEventCodes.UNCLASSIFIED;

    private static final Pattern EVENT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final int MAX_STACK_FRAMES = 64;
    private static final ObjectMapper JSON = new ObjectMapper();

    private String service = "unknown-service";
    private String environment = "default";
    private String serviceVersion = "unknown";
    private String instance;

    @Override
    public void start() {
        service = fallback(service, "unknown-service");
        environment = fallback(environment, "default");
        serviceVersion = fallback(serviceVersion, "unknown");
        instance = fallback(instance, defaultInstance());
        super.start();
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        if (event == null) return "";
        Map<String, String> mdc = event.getMDCPropertyMap() == null
                ? Map.of() : event.getMDCPropertyMap();
        Map<String, Object> structured = structured(event.getKeyValuePairs());
        IThrowableProxy throwable = event.getThrowableProxy();

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", SCHEMA_VERSION);
        value.put("timestamp", DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(event.getTimeStamp())));
        value.put("level", event.getLevel() == null ? "UNKNOWN" : event.getLevel().levelStr);
        value.put(LogFieldNames.EVENT_CODE,
                eventCode(text(structured, mdc, LogFieldNames.EVENT_CODE)));
        value.put("message", LogSanitizer.message(event.getFormattedMessage()));
        value.put("service", LogSanitizer.field(service));
        value.put("environment", LogSanitizer.field(environment));
        value.put("serviceVersion", LogSanitizer.field(serviceVersion));
        value.put("instance", LogSanitizer.field(instance));
        value.put("logger", LogSanitizer.field(event.getLoggerName()));
        value.put("thread", LogSanitizer.field(event.getThreadName()));

        value.put(LogFieldNames.TRACE_ID, identifier(structured, mdc, LogFieldNames.TRACE_ID));
        value.put(LogFieldNames.SW_TRACE_ID, identifier(structured, mdc, LogFieldNames.SW_TRACE_ID));
        value.put(LogFieldNames.SW_SPAN_ID, identifier(structured, mdc, LogFieldNames.SW_SPAN_ID));
        value.put(LogFieldNames.REQUEST_ID, identifier(structured, mdc, LogFieldNames.REQUEST_ID));
        value.put(LogFieldNames.OPERATION_ID, identifier(structured, mdc, LogFieldNames.OPERATION_ID));

        value.put(LogFieldNames.HTTP_METHOD, text(structured, mdc, LogFieldNames.HTTP_METHOD));
        value.put(LogFieldNames.ROUTE_TEMPLATE, text(structured, mdc, LogFieldNames.ROUTE_TEMPLATE));
        value.put(LogFieldNames.STATUS_CODE, integer(structured, mdc, LogFieldNames.STATUS_CODE));
        value.put(LogFieldNames.DURATION_MS, longValue(structured, mdc, LogFieldNames.DURATION_MS));
        value.put(LogFieldNames.ERROR_CODE, text(structured, mdc, LogFieldNames.ERROR_CODE));

        value.put(LogFieldNames.BUSINESS_TYPE, text(structured, mdc, LogFieldNames.BUSINESS_TYPE));
        value.put(LogFieldNames.BUSINESS_ID, identifier(structured, mdc, LogFieldNames.BUSINESS_ID));
        value.put(LogFieldNames.DOMAIN_TASK_ID, identifier(structured, mdc, LogFieldNames.DOMAIN_TASK_ID));
        value.put(LogFieldNames.ATTEMPT_NO, integer(structured, mdc, LogFieldNames.ATTEMPT_NO));
        value.put(LogFieldNames.EVENT_ID, identifier(structured, mdc, LogFieldNames.EVENT_ID));
        value.put(LogFieldNames.AI_CALL_ID, identifier(structured, mdc, LogFieldNames.AI_CALL_ID));
        value.put(LogFieldNames.MESSAGE_TOPIC, text(structured, mdc, LogFieldNames.MESSAGE_TOPIC));
        value.put(LogFieldNames.CONSUMER_GROUP, text(structured, mdc, LogFieldNames.CONSUMER_GROUP));
        value.put(LogFieldNames.RETRY_COUNT, integer(structured, mdc, LogFieldNames.RETRY_COUNT));
        value.put(LogFieldNames.SUPPRESSED_COUNT,
                longValue(structured, mdc, LogFieldNames.SUPPRESSED_COUNT));
        value.put(LogFieldNames.TASK_STATE, text(structured, mdc, LogFieldNames.TASK_STATE));
        value.put(LogFieldNames.CLAIM_TYPE, text(structured, mdc, LogFieldNames.CLAIM_TYPE));
        value.put(LogFieldNames.AI_PRIORITY, text(structured, mdc, LogFieldNames.AI_PRIORITY));
        value.put(LogFieldNames.AI_CALL_TYPE, text(structured, mdc, LogFieldNames.AI_CALL_TYPE));
        value.put(LogFieldNames.OUTCOME, text(structured, mdc, LogFieldNames.OUTCOME));

        value.put(LogFieldNames.EXCEPTION_TYPE, throwable == null
                ? text(structured, mdc, LogFieldNames.EXCEPTION_TYPE) : throwable.getClassName());
        value.put(LogFieldNames.FAILURE_CATEGORY,
                text(structured, mdc, LogFieldNames.FAILURE_CATEGORY));
        value.put("stackTrace", stackFrames(throwable));

        try {
            return JSON.writeValueAsString(value) + System.lineSeparator();
        } catch (JsonProcessingException exception) {
            addError("Unable to encode structured log event", exception);
            return "{\"schemaVersion\":\"" + SCHEMA_VERSION
                    + "\",\"level\":\"ERROR\",\"eventCode\":\"LOG_ENCODING_FAILED\"}"
                    + System.lineSeparator();
        }
    }

    public void setService(String service) {
        this.service = service;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    private Map<String, Object> structured(List<KeyValuePair> pairs) {
        if (pairs == null || pairs.isEmpty()) return Map.of();
        Map<String, Object> values = new HashMap<>();
        for (KeyValuePair pair : pairs) {
            if (pair != null && pair.key != null) values.put(pair.key, pair.value);
        }
        return values;
    }

    private String text(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Object structuredValue = structured.get(key);
        if (structuredValue != null) return LogSanitizer.field(String.valueOf(structuredValue));
        String mdcValue = mdc.get(key);
        return mdcValue == null || mdcValue.isBlank() ? null : LogSanitizer.field(mdcValue);
    }

    private String identifier(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Object structuredValue = structured.get(key);
        if (structuredValue != null) return LogSanitizer.identifier(String.valueOf(structuredValue));
        String mdcValue = mdc.get(key);
        return mdcValue == null || mdcValue.isBlank() ? null : LogSanitizer.identifier(mdcValue);
    }

    private Integer integer(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Long value = number(structured, mdc, key);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
        return value.intValue();
    }

    private Long longValue(Map<String, Object> structured, Map<String, String> mdc, String key) {
        return number(structured, mdc, key);
    }

    private Long number(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Object structuredValue = structured.get(key);
        if (structuredValue instanceof Number number) return number.longValue();
        String value = structuredValue == null ? mdc.get(key) : String.valueOf(structuredValue);
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String eventCode(String value) {
        if (value == null) return UNCLASSIFIED_EVENT;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return EVENT_CODE.matcher(normalized).matches() ? normalized : UNCLASSIFIED_EVENT;
    }

    private List<String> stackFrames(IThrowableProxy throwable) {
        if (throwable == null || throwable.getStackTraceElementProxyArray() == null) return null;
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        int size = Math.min(frames.length, MAX_STACK_FRAMES);
        java.util.ArrayList<String> safeFrames = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            safeFrames.add(LogSanitizer.stackFrame(frames[index].getStackTraceElement().toString()));
        }
        return List.copyOf(safeFrames);
    }

    private String defaultInstance() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) host = "localhost";
        return host + ":" + ProcessHandle.current().pid();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
