package com.leetmodel.common.core.telemetry;

import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

/**
 * 统一管理 HTTP、Feign、消息和 Worker 的关联 MDC 作用域。
 */
public final class CorrelationContext {

    /** Reactor Context 中用于自动恢复 MDC 的稳定 Key。 */
    public static final String REACTOR_CONTEXT_KEY = "leetmodel.correlation";
    /** HTTP 跨服务 traceId 头。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** HTTP 跨服务 operationId 头。 */
    public static final String OPERATION_ID_HEADER = "X-Operation-Id";
    /** 公网边界必须删除的内部关联头。 */
    public static final List<String> INTERNAL_CORRELATION_HEADERS = List.of(
            TRACE_ID_HEADER,
            OPERATION_ID_HEADER,
            "X-Sw-Trace-Id",
            "X-Sw-Span-Id",
            "X-Event-Id",
            "X-Domain-Task-Id",
            "X-Attempt-No",
            "X-Ai-Call-Id"
    );

    private static final String TRACE_ID = "traceId";
    private static final String SW_TRACE_ID = "swTraceId";
    private static final String SW_SPAN_ID = "swSpanId";
    private static final String OPERATION_ID = "operationId";
    private static final String EVENT_ID = "eventId";
    private static final String DOMAIN_TASK_ID = "domainTaskId";
    private static final String ATTEMPT_NO = "attemptNo";
    private static final String AI_CALL_ID = "aiCallId";
    private static final List<String> MDC_KEYS = List.of(
            TRACE_ID, SW_TRACE_ID, SW_SPAN_ID, OPERATION_ID, EVENT_ID,
            DOMAIN_TASK_ID, ATTEMPT_NO, AI_CALL_ID
    );

    private CorrelationContext() {
    }

    /**
     * 生成新的低信息量不可预测关联标识。
     *
     * @return 32 位 UUID 字符串
     */
    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从当前线程 MDC 捕获关联快照。
     *
     * @return 不可变快照
     */
    public static CorrelationSnapshot capture() {
        Integer attemptNo = positiveInteger(MDC.get(ATTEMPT_NO));
        return new CorrelationSnapshot(
                MDC.get(TRACE_ID),
                MDC.get(SW_TRACE_ID),
                MDC.get(SW_SPAN_ID),
                MDC.get(OPERATION_ID),
                MDC.get(EVENT_ID),
                MDC.get(DOMAIN_TASK_ID),
                attemptNo,
                MDC.get(AI_CALL_ID)
        );
    }

    /**
     * 打开一个可自动恢复先前 MDC 的关联作用域。
     *
     * @param snapshot 新快照
     * @return 必须关闭的作用域
     */
    public static Scope open(CorrelationSnapshot snapshot) {
        CorrelationSnapshot previous = capture();
        replace(snapshot == null ? CorrelationSnapshot.EMPTY : snapshot);
        return new Scope(previous);
    }

    /**
     * 完整替换当前线程的关联 MDC。
     *
     * @param snapshot 新快照
     */
    public static void replace(CorrelationSnapshot snapshot) {
        clear();
        if (snapshot == null) return;
        put(TRACE_ID, snapshot.traceId());
        put(SW_TRACE_ID, snapshot.swTraceId());
        put(SW_SPAN_ID, snapshot.swSpanId());
        put(OPERATION_ID, snapshot.operationId());
        put(EVENT_ID, snapshot.eventId());
        put(DOMAIN_TASK_ID, snapshot.domainTaskId());
        put(ATTEMPT_NO, snapshot.attemptNo() == null ? null : snapshot.attemptNo().toString());
        put(AI_CALL_ID, snapshot.aiCallId());
    }

    /** 清除当前线程的全部公共关联字段。 */
    public static void clear() {
        MDC_KEYS.forEach(MDC::remove);
    }

    /** @return 当前 traceId */
    public static String traceId() {
        return MDC.get(TRACE_ID);
    }

    /** @return 当前 operationId */
    public static String operationId() {
        return MDC.get(OPERATION_ID);
    }

    /**
     * 返回已建立的 operationId，或为新的受信治理命令生成一个。
     *
     * @return 当前 operationId
     */
    public static String ensureOperationId() {
        String current = operationId();
        if (current != null) return current;
        String generated = newId();
        setOperationId(generated);
        return generated;
    }

    /**
     * 替换当前 traceId，保留其他关联字段。
     *
     * @param traceId 业务关联标识
     */
    public static void setTraceId(String traceId) {
        String valid = TelemetryFieldPolicy.optionalCorrelationId(traceId, "traceId", 100);
        if (valid == null) MDC.remove(TRACE_ID); else MDC.put(TRACE_ID, valid);
    }

    /**
     * 替换当前 operationId，保留其他关联字段。
     *
     * @param operationId 治理操作标识
     */
    public static void setOperationId(String operationId) {
        String valid = TelemetryFieldPolicy.optionalCorrelationId(operationId, "operationId", 100);
        if (valid == null) MDC.remove(OPERATION_ID); else MDC.put(OPERATION_ID, valid);
    }

    /**
     * 判断一个 HTTP 关联头是否满足公共契约。
     *
     * @param value 头值
     * @return 是否合法
     */
    public static boolean isValidHttpId(String value) {
        return TelemetryFieldPolicy.isValidCorrelationId(value, 100);
    }

    private static void put(String key, String value) {
        if (value != null) MDC.put(key, value);
    }

    private static Integer positiveInteger(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 恢复前一个关联快照的作用域。 */
    public static final class Scope implements AutoCloseable {
        private final CorrelationSnapshot previous;
        private boolean closed;

        private Scope(CorrelationSnapshot previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            replace(previous);
            closed = true;
        }
    }
}
