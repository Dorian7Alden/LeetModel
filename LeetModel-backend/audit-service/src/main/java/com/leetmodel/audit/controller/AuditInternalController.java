package com.leetmodel.audit.controller;

import com.leetmodel.audit.repository.AuditQueryRepository;
import com.leetmodel.audit.service.AuditRetentionPolicyService;
import com.leetmodel.common.api.dto.OperationAuditPageDTO;
import com.leetmodel.common.api.dto.OperationAuditQueryDTO;
import com.leetmodel.common.api.dto.OperationAuditRetentionPolicyDTO;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 仅提供给受信 admin-service 的审计只读内部 API。 */
@Validated
@RestController
@RequestMapping("/internal/audit")
public class AuditInternalController {
    private final AuditQueryRepository repository;
    private final AuditRetentionPolicyService retention;

    public AuditInternalController(AuditQueryRepository repository, AuditRetentionPolicyService retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @GetMapping("/events")
    public Result<OperationAuditPageDTO> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String sourceService,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String operationCode,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String operationId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String swTraceId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit) {
        return Result.ok(repository.search(new OperationAuditQueryDTO(from, to, sourceService, category,
                operationCode, riskLevel, actorId, targetType, targetId, outcome, operationId, traceId,
                swTraceId, limit)));
    }

    @GetMapping("/retention-policy")
    public Result<OperationAuditRetentionPolicyDTO> retentionPolicy() {
        return Result.ok(retention.current());
    }
}
