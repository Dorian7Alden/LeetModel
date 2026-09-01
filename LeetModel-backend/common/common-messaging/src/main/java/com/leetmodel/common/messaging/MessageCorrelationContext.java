package com.leetmodel.common.messaging;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;

/**
 * 从已通过契约校验的消息信封恢复消费线程关联上下文。
 */
public final class MessageCorrelationContext {

    private MessageCorrelationContext() {
    }

    /**
     * 恢复信封的 traceId、operationId 和 eventId。
     *
     * @param envelope 已校验信封
     * @return 必须关闭的作用域
     */
    public static CorrelationContext.Scope open(MessageEnvelopeV1<?> envelope) {
        return open(envelope, null, null, null);
    }

    /**
     * 恢复信封和载荷中已持久化的领域任务关联字段。
     *
     * @param envelope 已校验信封
     * @param domainTaskId 领域任务标识
     * @param attemptNo 物理尝试序号
     * @param aiCallId AI 原子调用标识
     * @return 必须关闭的作用域
     */
    public static CorrelationContext.Scope open(
            MessageEnvelopeV1<?> envelope,
            String domainTaskId,
            Integer attemptNo,
            String aiCallId
    ) {
        if (envelope == null) throw new IllegalArgumentException("message envelope is required");
        CorrelationSnapshot snapshot = CorrelationSnapshot.EMPTY
                .withTraceId(envelope.traceId())
                .withMessage(envelope.eventId(), envelope.operationId())
                .withDomainTask(domainTaskId, attemptNo)
                .withAiCallId(aiCallId);
        return CorrelationContext.open(snapshot);
    }
}
