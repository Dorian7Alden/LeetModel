package com.leetmodel.common.core.telemetry;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.ContextCarrierRef;
import org.apache.skywalking.apm.toolkit.trace.SpanRef;
import org.apache.skywalking.apm.toolkit.trace.Tracer;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 为消息、租约 Worker 和 AI provider 的一次物理执行创建短小的 Entry Span。
 *
 * <p>每次调用均使用空 Carrier，不能把跨队列等待或上一任租约的 Trace 延长到当前
 * attempt。业务标识仅恢复到 MDC/持久化关联上下文，不写入 Span tag。没有 Agent、
 * 未采样或 Agent API 异常时，本类保持业务 fail-open。</p>
 */
public final class SkyWalkingExecutionSpan implements AutoCloseable {

    private static final Set<String> ALLOWED_TAG_KEYS = Set.of(
            "attempt.kind", "ai.call_type", "ai.priority", "outcome", "error.kind"
    );
    private static final Pattern LOW_CARDINALITY_VALUE = Pattern.compile(
            "[A-Za-z][A-Za-z0-9._-]{0,63}"
    );

    private final CorrelationContext.Scope correlationScope;
    private final SpanRef span;
    private final boolean started;
    private boolean closed;

    private SkyWalkingExecutionSpan(
            CorrelationContext.Scope correlationScope,
            SpanRef span,
            boolean started
    ) {
        this.correlationScope = correlationScope;
        this.span = span;
        this.started = started;
    }

    /**
     * 使用当前业务关联上下文开启新的物理执行 Trace。
     *
     * @param operation 固定 operation
     * @return 必须关闭的 Span 作用域
     */
    public static SkyWalkingExecutionSpan open(ExecutionSpanOperation operation) {
        return open(operation, CorrelationContext.capture());
    }

    /**
     * 从持久化事实恢复业务关联字段并开启新的物理执行 Trace。
     *
     * @param operation 固定 operation
     * @param correlation 持久化业务关联快照
     * @return 必须关闭的 Span 作用域
     */
    public static SkyWalkingExecutionSpan open(
            ExecutionSpanOperation operation,
            CorrelationSnapshot correlation
    ) {
        if (operation == null) throw new IllegalArgumentException("execution span operation is required");
        CorrelationContext.Scope scope = CorrelationContext.open(
                correlation == null ? CorrelationSnapshot.EMPTY : correlation);
        SpanRef created = null;
        boolean spanStarted = false;
        try {
            created = Tracer.createEntrySpan(operation.operationName(), new ContextCarrierRef());
            spanStarted = true;
            SkyWalkingCorrelation.bindBusinessTraceId(CorrelationContext.traceId());
            CorrelationContext.replace(SkyWalkingCorrelation.enrich(CorrelationContext.capture()));
        } catch (RuntimeException | LinkageError ignored) {
            // Agent/Toolkit 不可用不能影响消息 ACK、租约状态或 AI 结果。
        }
        return new SkyWalkingExecutionSpan(scope, created, spanStarted);
    }

    /** 记录正常领取或租约接管，不记录任务标识。 */
    public SkyWalkingExecutionSpan attemptKind(boolean takeover) {
        return tag("attempt.kind", takeover ? "takeover" : "normal");
    }

    /** 记录固定 AI 调用类型。 */
    public SkyWalkingExecutionSpan aiCallType(String callType) {
        return tag("ai.call_type", normalized(callType));
    }

    /** 记录 P0-P4 固定优先级。 */
    public SkyWalkingExecutionSpan aiPriority(String priority) {
        return tag("ai.priority", normalized(priority));
    }

    /** 记录固定结果分类。 */
    public SkyWalkingExecutionSpan outcome(String outcome) {
        return tag("outcome", normalized(outcome));
    }

    /**
     * 标记错误并只记录固定错误分类；不接收 Throwable，避免原始异常和消息泄漏。
     */
    public SkyWalkingExecutionSpan error(String errorKind) {
        tag("error.kind", normalized(errorKind));
        if (started) {
            try {
                ActiveSpan.error();
            } catch (RuntimeException | LinkageError ignored) {
                // fail-open
            }
        }
        return this;
    }

    private SkyWalkingExecutionSpan tag(String key, String value) {
        if (!started || span == null || !ALLOWED_TAG_KEYS.contains(key)
                || !isLowCardinalityValue(value)) return this;
        try {
            span.tag(key, value);
        } catch (RuntimeException | LinkageError ignored) {
            // fail-open
        }
        return this;
    }

    static boolean isLowCardinalityValue(String value) {
        return value != null && LOW_CARDINALITY_VALUE.matcher(value).matches();
    }

    private static String normalized(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        if (closed) return;
        try {
            if (started) Tracer.stopSpan();
        } catch (RuntimeException | LinkageError ignored) {
            // fail-open
        } finally {
            correlationScope.close();
            closed = true;
        }
    }
}
