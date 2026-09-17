package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.service.AdminAuditOperationsService;
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

/** 管理端统一审计只读入口；所有请求要求 admin 角色。 */
@Validated
@RestController
@RequestMapping("/api/admin/audit")
@SaCheckRole("admin")
public class AdminAuditController {
    private final AdminAuditOperationsService service;

    public AdminAuditController(AdminAuditOperationsService service) {
        this.service = service;
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
        return Result.ok(service.search(new OperationAuditQueryDTO(from, to, sourceService, category,
                operationCode, riskLevel, actorId, targetType, targetId, outcome, operationId, traceId,
                swTraceId, limit)));
    }

    @GetMapping("/retention-policy")
    public Result<OperationAuditRetentionPolicyDTO> retentionPolicy() {
        return Result.ok(service.retentionPolicy());
    }
}
