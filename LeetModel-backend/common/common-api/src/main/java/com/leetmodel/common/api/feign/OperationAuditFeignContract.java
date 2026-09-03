package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.OperationAuditPageDTO;
import com.leetmodel.common.api.dto.OperationAuditRetentionPolicyDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** admin-service 到 audit-service 的只读内部契约。 */
public interface OperationAuditFeignContract {
    @GetMapping("/internal/audit/events")
    Result<OperationAuditPageDTO> search(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "sourceService", required = false) String sourceService,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "operationCode", required = false) String operationCode,
            @RequestParam(value = "riskLevel", required = false) String riskLevel,
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "operationId", required = false) String operationId,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "swTraceId", required = false) String swTraceId,
            @RequestParam("limit") Integer limit);

    @GetMapping("/internal/audit/retention-policy")
    Result<OperationAuditRetentionPolicyDTO> retentionPolicy();
}
