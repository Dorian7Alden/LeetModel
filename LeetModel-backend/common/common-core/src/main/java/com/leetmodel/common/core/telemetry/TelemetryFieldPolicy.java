package com.leetmodel.common.core.telemetry;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 遥测字段的长度、字符集与低基数约束。
 */
public final class TelemetryFieldPolicy {

    /** 通用关联标识的最大长度。 */
    public static final int MAX_CORRELATION_ID_LENGTH = 100;
    /** SkyWalking Trace 标识的最大长度。 */
    public static final int MAX_SKYWALKING_ID_LENGTH = 128;
    /** 稳定事件编码的最大长度。 */
    public static final int MAX_EVENT_CODE_LENGTH = 80;

    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");
    private static final Pattern RUNTIME_NUMBER_SEGMENT = Pattern.compile("(?:^|_)\\d{4,}(?:_|$)");
    private static final Pattern RESOURCE_VALUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Set<String> HIGH_CARDINALITY_LABELS = Set.of(
            "userid", "teamid", "problemid", "submissionid", "traceid", "swtraceid",
            "swspanid", "requestid", "operationid", "eventid", "domaintaskid",
            "attemptno", "aicallid", "businessid", "idempotencykey", "targetid"
    );

    private TelemetryFieldPolicy() {
    }

    /**
     * 校验并返回可传播的关联标识。
     *
     * @param value 原始标识
     * @param field 字段名
     * @param maxLength 最大长度
     * @return 去除首尾空白的标识；空值返回 null
     */
    public static String optionalCorrelationId(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength || !CORRELATION_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " contains unsupported characters or length");
        }
        return normalized;
    }

    /**
     * 判断输入是否是可信 HTTP 关联标识。
     *
     * @param value 标识
     * @param maxLength 最大长度
     * @return 是否合法
     */
    public static boolean isValidCorrelationId(String value, int maxLength) {
        try {
            return optionalCorrelationId(value, "correlationId", maxLength) != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 校验稳定事件编码。
     *
     * @param eventCode 事件编码
     * @return 原事件编码
     */
    public static String requireEventCode(String eventCode) {
        if (eventCode == null || eventCode.length() > MAX_EVENT_CODE_LENGTH
                || !EVENT_CODE.matcher(eventCode).matches()
                || RUNTIME_NUMBER_SEGMENT.matcher(eventCode).find()) {
            throw new IllegalArgumentException("eventCode must be stable UPPER_SNAKE_CASE");
        }
        return eventCode;
    }

    /**
     * 拒绝把请求或业务标识作为指标标签。
     *
     * @param labelName 指标标签名
     * @return 原标签名
     */
    public static String requireLowCardinalityLabel(String labelName) {
        if (labelName == null || labelName.isBlank()) {
            throw new IllegalArgumentException("metric label name is required");
        }
        String normalized = labelName.replace("_", "")
                .replace("-", "")
                .replace(".", "")
                .toLowerCase(Locale.ROOT);
        if (HIGH_CARDINALITY_LABELS.contains(normalized)) {
            throw new IllegalArgumentException(labelName + " is a high-cardinality metric label");
        }
        return labelName;
    }

    /**
     * 校验进程启动时确定的资源字段。
     *
     * @param value 资源值
     * @param field 字段名
     * @return 原资源值
     */
    public static String requireResourceValue(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 100
                || !RESOURCE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a stable resource value");
        }
        return value;
    }
}
