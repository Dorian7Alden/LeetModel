package com.leetmodel.common.core.util;

import com.leetmodel.common.core.telemetry.CorrelationContext;

/**
 * 全链路 TraceId 读写门面工具类。
 *
 * <p>底层委托 CorrelationContext 操作 SLF4J MDC，保持静态调用语法兼容。</p>
 */
public final class TraceIdUtil {

    /** 跨服务透传 TraceId 的标准 HTTP Header 名称 */
    public static final String TRACE_ID_HEADER = CorrelationContext.TRACE_ID_HEADER;

    private TraceIdUtil() {
        // 工具类禁止实例化
    }

    /**
     * 设置当前线程绑定的全局追踪 ID。
     *
     * @param traceId 链路追踪 ID，传 null 时清空
     */
    public static void setTraceId(String traceId) {
        CorrelationContext.setTraceId(traceId);
    }

    /**
     * 读取当前线程绑定的全局追踪 ID。
     *
     * @return 当前链路绑定的 traceId；未设置时返回 null
     */
    public static String getTraceId() {
        return CorrelationContext.traceId();
    }

    /**
     * 清除当前线程绑定的 traceId，防止线程复用污染。
     */
    public static void removeTraceId() {
        CorrelationContext.setTraceId(null);
    }
}
