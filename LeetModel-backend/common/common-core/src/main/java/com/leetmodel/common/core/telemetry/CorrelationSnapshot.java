package com.leetmodel.common.core.telemetry;

/**
 * 一次同步或异步执行的可传播关联字段快照。
 *
 * @param traceId LeetModel 业务关联标识
 * @param swTraceId SkyWalking Trace 标识
 * @param swSpanId SkyWalking Span 标识
 * @param operationId 治理操作标识
 * @param eventId 可靠消息事件标识
 * @param domainTaskId 领域任务标识
 * @param attemptNo 领域任务物理尝试序号
 * @param aiCallId AI 原子调用标识
 */
public record CorrelationSnapshot(
        String traceId,
        String swTraceId,
        String swSpanId,
        String operationId,
        String eventId,
        String domainTaskId,
        Integer attemptNo,
        String aiCallId
) {

    /** 空关联快照。 */
    public static final CorrelationSnapshot EMPTY = new CorrelationSnapshot(
            null, null, null, null, null, null, null, null
    );

    /** 对所有关联标识执行统一校验。 */
    public CorrelationSnapshot {
        traceId = TelemetryFieldPolicy.optionalCorrelationId(traceId, "traceId", 100);
        swTraceId = TelemetryFieldPolicy.optionalCorrelationId(swTraceId, "swTraceId", 128);
        swSpanId = TelemetryFieldPolicy.optionalCorrelationId(swSpanId, "swSpanId", 100);
        operationId = TelemetryFieldPolicy.optionalCorrelationId(operationId, "operationId", 100);
        eventId = TelemetryFieldPolicy.optionalCorrelationId(eventId, "eventId", 36);
        domainTaskId = TelemetryFieldPolicy.optionalCorrelationId(domainTaskId, "domainTaskId", 100);
        if (attemptNo != null && attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        aiCallId = TelemetryFieldPolicy.optionalCorrelationId(aiCallId, "aiCallId", 100);
    }

    /** @return 替换 traceId 后的新快照 */
    public CorrelationSnapshot withTraceId(String value) {
        return new CorrelationSnapshot(value, swTraceId, swSpanId, operationId, eventId,
                domainTaskId, attemptNo, aiCallId);
    }

    /** @return 替换 operationId 后的新快照 */
    public CorrelationSnapshot withOperationId(String value) {
        return new CorrelationSnapshot(traceId, swTraceId, swSpanId, value, eventId,
                domainTaskId, attemptNo, aiCallId);
    }

    /** @return 替换 SkyWalking 关联字段后的新快照 */
    public CorrelationSnapshot withSkyWalking(String newSwTraceId, String newSwSpanId) {
        return new CorrelationSnapshot(traceId, newSwTraceId, newSwSpanId, operationId, eventId,
                domainTaskId, attemptNo, aiCallId);
    }

    /** @return 替换消息关联字段后的新快照 */
    public CorrelationSnapshot withMessage(String newEventId, String newOperationId) {
        return new CorrelationSnapshot(traceId, swTraceId, swSpanId, newOperationId, newEventId,
                domainTaskId, attemptNo, aiCallId);
    }

    /** @return 替换领域任务字段后的新快照 */
    public CorrelationSnapshot withDomainTask(String taskId, Integer newAttemptNo) {
        return new CorrelationSnapshot(traceId, swTraceId, swSpanId, operationId, eventId,
                taskId, newAttemptNo, aiCallId);
    }

    /** @return 替换 aiCallId 后的新快照 */
    public CorrelationSnapshot withAiCallId(String value) {
        return new CorrelationSnapshot(traceId, swTraceId, swSpanId, operationId, eventId,
                domainTaskId, attemptNo, value);
    }
}
