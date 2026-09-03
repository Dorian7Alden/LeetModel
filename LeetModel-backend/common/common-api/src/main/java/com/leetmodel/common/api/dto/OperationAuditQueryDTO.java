package com.leetmodel.common.api.dto;

import java.time.Instant;

/** 中央审计查询过滤；所有字段均为精确匹配，时间范围为半开区间。 */
public record OperationAuditQueryDTO(
        Instant from,
        Instant to,
        String sourceService,
        String category,
        String operationCode,
        String riskLevel,
        String actorId,
        String targetType,
        String targetId,
        String outcome,
        String operationId,
        String traceId,
        String swTraceId,
        Integer limit
) { }
