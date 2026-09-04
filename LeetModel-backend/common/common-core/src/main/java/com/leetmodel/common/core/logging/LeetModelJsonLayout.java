package com.leetmodel.common.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.telemetry.SkyWalkingCorrelation;
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

    /**
     * 启动并初始化布局器，为各元数据字段设置默认值。
     */
    @Override
    public void start() {
        service = fallback(service, "unknown-service");
        environment = fallback(environment, "default");
        serviceVersion = fallback(serviceVersion, "unknown");
        instance = fallback(instance, defaultInstance());
        super.start();
    }

    /**
     * 将 Logback 日志事件格式化为单行白名单 JSON 字符串。
     *
     * @param event Logback 原始事件对象
     * @return 格式化后的单行 JSON 字符串；事件为空时返回空字符串
     */
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
        value.put(LogFieldNames.SW_TRACE_ID, firstIdentifier(
                identifier(structured, mdc, LogFieldNames.SW_TRACE_ID),
                SkyWalkingCorrelation.traceId()));
        value.put(LogFieldNames.SW_SPAN_ID, firstIdentifier(
                identifier(structured, mdc, LogFieldNames.SW_SPAN_ID),
                SkyWalkingCorrelation.spanId()));
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

    /**
     * 设置所属微服务名称。
     *
     * @param service 微服务名称
     */
    public void setService(String service) {
        this.service = service;
    }

    /**
     * 设置当前运行环境。
     *
     * @param environment 运行环境标识（如 dev、prod）
     */
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    /**
     * 设置微服务版本号。
     *
     * @param serviceVersion 服务版本号
     */
    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    /**
     * 设置当前运行实例标识。
     *
     * @param instance 实例标识字符串
     */
    public void setInstance(String instance) {
        this.instance = instance;
    }

    /**
     * 提取日志事件中携带的结构化键值对。
     *
     * @param pairs 键值对列表
     * @return 提取后的 Map 结构
     */
    private Map<String, Object> structured(List<KeyValuePair> pairs) {
        if (pairs == null || pairs.isEmpty()) return Map.of();
        Map<String, Object> values = new HashMap<>();
        for (KeyValuePair pair : pairs) {
            if (pair != null && pair.key != null) values.put(pair.key, pair.value);
        }
        return values;
    }

    /**
     * 从结构化参数或 MDC 中提取并清洗文本字段。
     *
     * @param structured 结构化参数映射
     * @param mdc        MDC 属性映射
     * @param key        字段键名
     * @return 脱敏清洗后的字段文本；不存在时返回 null
     */
    private String text(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Object structuredValue = structured.get(key);
        if (structuredValue != null) return LogSanitizer.field(String.valueOf(structuredValue));
        String mdcValue = mdc.get(key);
        return mdcValue == null || mdcValue.isBlank() ? null : LogSanitizer.field(mdcValue);
    }

    /**
     * 从结构化参数或 MDC 中提取并清洗关联标识字段。
     *
     * @param structured 结构化参数映射
     * @param mdc        MDC 属性映射
     * @param key        字段键名
     * @return 脱敏清洗后的标识文本；不存在时返回 null
     */
    private String identifier(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Object structuredValue = structured.get(key);
        if (structuredValue != null) return LogSanitizer.identifier(String.valueOf(structuredValue));
        String mdcValue = mdc.get(key);
        return mdcValue == null || mdcValue.isBlank() ? null : LogSanitizer.identifier(mdcValue);
    }

    /**
     * 优先选取首选标识，若不存在则回退至备选标识。
     *
     * @param preferred 优先标识
     * @param fallback  回退标识
     * @return 有效的标识文本
     */
    private String firstIdentifier(String preferred, String fallback) {
        return preferred == null ? LogSanitizer.identifier(fallback) : preferred;
    }

    /**
     * 提取整型数值字段。
     *
     * @param structured 结构化参数映射
     * @param mdc        MDC 属性映射
     * @param key        字段键名
     * @return 解析后的 Integer 值；不存在或溢出时返回 null
     */
    private Integer integer(Map<String, Object> structured, Map<String, String> mdc, String key) {
        Long value = number(structured, mdc, key);
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
        return value.intValue();
    }

    /**
     * 提取长整型数值字段。
     *
     * @param structured 结构化参数映射
     * @param mdc        MDC 属性映射
     * @param key        字段键名
     * @return 解析后的 Long 值；不存在时返回 null
     */
    private Long longValue(Map<String, Object> structured, Map<String, String> mdc, String key) {
        return number(structured, mdc, key);
    }

    /**
     * 提取数值并转换为 Long。
     *
     * @param structured 结构化参数映射
     * @param mdc        MDC 属性映射
     * @param key        字段键名
     * @return 解析后的 Long 值；非数值或不存在时返回 null
     */
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

    /**
     * 校验并提取标准事件编码。
     *
     * @param value 原始事件编码值
     * @return 校验通过的大写编码；非法时返回 UNCLASSIFIED
     */
    private String eventCode(String value) {
        if (value == null) return UNCLASSIFIED_EVENT;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return EVENT_CODE.matcher(normalized).matches() ? normalized : UNCLASSIFIED_EVENT;
    }

    /**
     * 格式化并截断异常堆栈代码帧，屏蔽异常明细文本。
     *
     * @param throwable 异常代理对象
     * @return 最多 64 帧的安全堆栈行列表
     */
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

    /**
     * 构建默认实例标识（主机名加进程 ID）。
     *
     * @return 格式为 host:pid 的实例标识
     */
    private String defaultInstance() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) host = "localhost";
        return host + ":" + ProcessHandle.current().pid();
    }

    /**
     * 空值回退辅助函数。
     *
     * @param value    首选值
     * @param fallback 默认回退值
     * @return 非空时的首选值或回退值
     */
    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
