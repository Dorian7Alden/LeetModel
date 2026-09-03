package com.leetmodel.common.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 中央审计只读投影；不包含正文、凭据或完整业务快照。 */
public record OperationAuditEventDTO(
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
        Instant archivedAt
) { }
