package com.leetmodel.common.core.telemetry;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;

import java.util.Set;

/**
 * 将 SkyWalking Agent 当前物理执行标识投影到公共关联字段。
 *
 * <p>Toolkit 在未附加 Agent 时返回空值；附加 Agent 后由 activation 插件增强，
 * 因此本类不启动独立追踪器、线程或 exporter。</p>
 */
public final class SkyWalkingCorrelation {

    /** SkyWalking Trace 上关联业务 traceId 的固定高基数 Tag，仅用于精确检索。 */
    public static final String BUSINESS_TRACE_TAG = "business.trace_id";

    private static final Set<String> NON_TRACE_VALUES = Set.of(
            "N/A", "Ignored_Trace", "NO_ACTIVE_SPAN"
    );

    private SkyWalkingCorrelation() {
    }

    /**
     * 读取当前 SkyWalking 物理执行链路的 Trace ID。
     *
     * @return 可查询的 SkyWalking Trace ID；未采样或无 Agent 探针时返回 null
     */
    public static String traceId() {
        try {
            return usable(TraceContext.traceId(), TelemetryFieldPolicy.MAX_SKYWALKING_ID_LENGTH);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 返回由 segmentId 与局部 spanId 组成的稳定 Span 引用。
     *
     * @return 当前 Span 引用；无活动 Span 时返回 null
     */
    public static String spanId() {
        try {
            String segmentId = usable(TraceContext.segmentId(),
                    TelemetryFieldPolicy.MAX_SKYWALKING_ID_LENGTH);
            int spanId = TraceContext.spanId();
            if (segmentId == null || spanId < 0) return null;
            return usable(segmentId + ":" + spanId,
                    TelemetryFieldPolicy.MAX_SKYWALKING_ID_LENGTH);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 将当前线程的 SkyWalking 链路标识丰富注入到既有业务关联快照中。
     *
     * @param snapshot 原始业务关联快照，传 null 时视为空快照
     * @return 注入了当前 Agent TraceId 与 SpanId 的新快照实例
     */
    public static CorrelationSnapshot enrich(CorrelationSnapshot snapshot) {
        CorrelationSnapshot base = snapshot == null ? CorrelationSnapshot.EMPTY : snapshot;
        return base.withSkyWalking(traceId(), spanId());
    }

    /**
     * 将业务 traceId 绑定到 SkyWalking correlation context。
     *
     * <p>该值只进入 Trace 精确检索，不进入 Prometheus 标签。</p>
     *
     * @param businessTraceId 已通过公共关联契约校验的业务 traceId
     */
    public static void bindBusinessTraceId(String businessTraceId) {
        String value = usable(businessTraceId, TelemetryFieldPolicy.MAX_CORRELATION_ID_LENGTH);
        if (value == null) return;
        try {
            TraceContext.putCorrelation(BUSINESS_TRACE_TAG, value);
        } catch (RuntimeException | LinkageError ignored) {
            // 未附加 Agent、当前上下文不可用或 Agent 故障时保持业务 fail-open。
        }
    }

    /**
     * 校验并提取可用的 SkyWalking 链路标识字符串。
     *
     * @param value     原始标识字符串
     * @param maxLength 允许的最大长度上限
     * @return 经过合法性与长度校验的标识；非法或属于无效占位符时返回 null
     */
    static String usable(String value, int maxLength) {
        if (value == null || value.isBlank() || NON_TRACE_VALUES.contains(value)) return null;
        try {
            return TelemetryFieldPolicy.optionalCorrelationId(
                    value, "skyWalkingId", maxLength);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
