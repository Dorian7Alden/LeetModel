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

    /**
     * 构造审计内部查询 Controller。
     *
     * @param repository 审计查询仓储
     * @param retention  归档留存策略服务
     */
    public AuditInternalController(AuditQueryRepository repository, AuditRetentionPolicyService retention) {
        this.repository = repository;
        this.retention = retention;
    }

    /**
     * 组合多维度条件检索操作审计日志记录（只读）。
     *
     * @param from          起始时间
     * @param to            截止时间
     * @param sourceService 来源微服务
     * @param category      操作大类
     * @param operationCode 具体操作码
     * @param riskLevel     风险等级（HIGH/MEDIUM/LOW）
     * @param actorId       操作人标识
     * @param targetType    操作目标实体类型
     * @param targetId      操作目标标识
     * @param outcome       操作结果（SUCCEEDED/FAILED/REJECTED）
     * @param operationId   全局治理操作唯一 ID
     * @param traceId       链路追踪 ID
     * @param swTraceId     SkyWalking 追踪 ID
     * @param limit         单次拉取数量上限
     * @return 分页包装的审计事件列表
     */
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

    /**
     * 查询当前平台生效的操作审计保留与冷归档策略配置。
     *
     * @return 归档保留策略 DTO
     */
    @GetMapping("/retention-policy")
    public Result<OperationAuditRetentionPolicyDTO> retentionPolicy() {
        return Result.ok(retention.current());
    }
}
