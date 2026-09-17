package com.leetmodel.common.api.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 操作审计的版本化、最小化业务载荷。
 *
 * <p>此载荷只表达语义审计事实；不包含请求/响应正文、业务实体快照、幂等键、密码、
 * Token、Prompt、回答或消息 Payload。生产者和 archive consumer 均必须先调用
 * {@link OperationAuditContract#validate(OperationAuditPayloadV1)}。</p>
 */
public record OperationAuditPayloadV1(
        int auditSchemaVersion,
        String auditEventId,
        String operationId,
        String phase,
        Instant occurredAt,
        String sourceService,
        String serviceVersion,
        String category,
        String operationCode,
        String riskLevel,
        String outcome,
        String reason,
        String failureCode,
        String actorType,
        String actorId,
        List<String> actorRolesSnapshot,
        String targetType,
        String targetId,
        String targetVersion,
        Map<String, String> beforeSummary,
        Map<String, String> afterSummary,
        String traceId,
        String swTraceId,
        String requestId,
        String domainTaskId,
        String relatedEventId,
        String clientIpHash,
        String userAgentHash
) {

    /** 当前审计载荷版本。 */
    public static final int VERSION = 1;

    public OperationAuditPayloadV1 {
        actorRolesSnapshot = actorRolesSnapshot == null ? List.of() : List.copyOf(actorRolesSnapshot);
        beforeSummary = beforeSummary == null ? Map.of() : Map.copyOf(beforeSummary);
        afterSummary = afterSummary == null ? Map.of() : Map.copyOf(afterSummary);
    }

    /** 生成与公共消息 eventId 一致的载荷副本。 */
    public OperationAuditPayloadV1 withAuditEventId(String eventId) {
        return new OperationAuditPayloadV1(auditSchemaVersion, eventId, operationId, phase,
                occurredAt, sourceService, serviceVersion, category, operationCode, riskLevel,
                outcome, reason, failureCode, actorType, actorId, actorRolesSnapshot, targetType,
                targetId, targetVersion, beforeSummary, afterSummary, traceId, swTraceId,
                requestId, domainTaskId, relatedEventId, clientIpHash, userAgentHash);
    }
}
