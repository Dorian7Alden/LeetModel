package com.leetmodel.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.assistant.entity.AssistantProductionAudit;
import com.leetmodel.assistant.entity.AssistantProductionChangeRequest;
import com.leetmodel.assistant.entity.AssistantProductionConfig;
import com.leetmodel.assistant.entity.AssistantProductionPointer;
import com.leetmodel.assistant.entity.AssistantWorkflowVersion;
import com.leetmodel.assistant.enums.AssistantErrorCode;
import com.leetmodel.assistant.mapper.AssistantProductionAuditMapper;
import com.leetmodel.assistant.mapper.AssistantProductionChangeRequestMapper;
import com.leetmodel.assistant.mapper.AssistantProductionConfigMapper;
import com.leetmodel.assistant.mapper.AssistantProductionPointerMapper;
import com.leetmodel.assistant.mapper.AssistantWorkflowVersionMapper;
import com.leetmodel.common.api.dto.AssistantProductionChangeResultDTO;
import com.leetmodel.common.api.dto.AssistantProductionConfigDTO;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 只在 lm_ai_assistant 内完成指针、请求结果和审计的原子提交。 */
@Service
public class AssistantProductionChangeTransactionService {

    private static final long POINTER_ID = 1L;

    private final AssistantProductionChangeRequestMapper changeMapper;
    private final AssistantProductionPointerMapper pointerMapper;
    private final AssistantProductionConfigMapper configMapper;
    private final AssistantWorkflowVersionMapper workflowMapper;
    private final AssistantProductionAuditMapper auditMapper;

    public AssistantProductionChangeTransactionService(
            AssistantProductionChangeRequestMapper changeMapper,
            AssistantProductionPointerMapper pointerMapper,
            AssistantProductionConfigMapper configMapper,
            AssistantWorkflowVersionMapper workflowMapper,
            AssistantProductionAuditMapper auditMapper) {
        this.changeMapper = changeMapper;
        this.pointerMapper = pointerMapper;
        this.configMapper = configMapper;
        this.workflowMapper = workflowMapper;
        this.auditMapper = auditMapper;
    }

    /**
     * 在业务 owner 的单个本地事务内确认生产配置变更。
     * @param changeRequestId 服务端冻结的变更请求标识
     * @param operatorId 当前登录管理员
     * @param dependenciesReady 事务外重检的依赖可用性
     * @return 变更终态与当前生产配置
     */
    @Transactional
    public AssistantProductionChangeResultDTO apply(String changeRequestId, Long operatorId,
                                                    boolean dependenciesReady) {
        // 锁定变更请求，保证同一确认只能进入一次终态
        LocalDateTime now = LocalDateTime.now();
        AssistantProductionChangeRequest change =
                changeMapper.selectByRequestIdForUpdate(changeRequestId);
        BusinessException.throwIf(change == null || !operatorId.equals(change.getOperatorId()),
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);

        if (!"PENDING".equals(change.getStatus())) return existingResult(change);
        if (!change.getExpiresAt().isAfter(now)) {
            finish(change, "EXPIRED", "变更请求已过期", now);
            return result(change, null, "变更请求已过期");
        }
        // 使用当前读锁定唯一指针，避免冲突响应读取旧快照
        AssistantProductionPointer pointer = pointerMapper.selectByIdForUpdate(POINTER_ID);
        BusinessException.throwIf(pointer == null,
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        AssistantProductionConfig target = configMapper.selectById(change.getTargetConfigId());
        AssistantWorkflowVersion workflow = target == null ? null : workflow(target.getWorkflowVersion());
        if (!dependenciesReady || target == null || workflow == null
                || !"ENABLED".equals(workflow.getStatus())) {
            finish(change, "REJECTED", "目标配置依赖不可用", now);
            return result(change, current(pointer), "目标配置依赖不可用");
        }

        // 条件移动指针；旧 revision 的并发请求只能得到冲突终态
        LocalDateTime observationUntil = now.plusMinutes(10);
        int updated = pointerMapper.activate(change.getSourceConfigId(), change.getTargetConfigId(),
                change.getExpectedRevision(), operatorId, now, observationUntil);
        if (updated == 0) {
            finish(change, "CONFLICT", "生产配置已变化", now);
            return result(change, current(pointer), "生产配置已变化");
        }

        // 指针、成功审计和请求终态由同一事务提交
        AssistantProductionAudit audit = new AssistantProductionAudit();
        audit.setChangeRequestId(change.getChangeRequestId());
        audit.setAction(change.getAction());
        audit.setFromConfigId(change.getSourceConfigId());
        audit.setToConfigId(change.getTargetConfigId());
        audit.setFromRevision(change.getExpectedRevision());
        audit.setToRevision(change.getExpectedRevision() + 1);
        audit.setOperatorId(operatorId);
        audit.setReason(change.getReason());
        audit.setChangedAt(now);
        audit.setCreateTime(now);
        audit.setUpdateTime(now);
        auditMapper.insert(audit);
        finish(change, "APPLIED", "生产配置已生效", now);
        pointer.setActiveConfigId(change.getTargetConfigId());
        pointer.setRevision(change.getExpectedRevision() + 1);
        pointer.setActivatedBy(operatorId);
        pointer.setActivatedAt(now);
        pointer.setObservationUntil(observationUntil);
        return new AssistantProductionChangeResultDTO(change.getChangeRequestId(), "APPLIED",
                "生产配置已生效", current(pointer), audit.getId());
    }

    /**
     * 将幂等重复确认转换为已经持久化的终态结果。
     * @param change 已经进入终态的变更请求
     * @return 原终态及当前生产配置
     */
    private AssistantProductionChangeResultDTO existingResult(
            AssistantProductionChangeRequest change) {
        AssistantProductionAudit audit = auditMapper.selectOne(
                new LambdaQueryWrapper<AssistantProductionAudit>()
                        .eq(AssistantProductionAudit::getChangeRequestId,
                                change.getChangeRequestId()));
        return new AssistantProductionChangeResultDTO(change.getChangeRequestId(),
                change.getStatus(), change.getResultMessage(),
                current(pointerMapper.selectById(POINTER_ID)), audit == null ? null : audit.getId());
    }

    /**
     * 组装没有成功审计的终态结果。
     * @param change 变更请求
     * @param current 当前生产配置
     * @param message 面向管理员的结果说明
     * @return 变更结果
     */
    private AssistantProductionChangeResultDTO result(AssistantProductionChangeRequest change,
                                                       AssistantProductionConfigDTO current,
                                                       String message) {
        return new AssistantProductionChangeResultDTO(change.getChangeRequestId(),
                change.getStatus(), message, current, null);
    }

    /**
     * 保存变更请求终态，只有真正生效时记录 appliedAt。
     * @param change 变更请求
     * @param status 目标终态
     * @param message 结果说明
     * @param now 完成时间
     */
    private void finish(AssistantProductionChangeRequest change, String status,
                        String message, LocalDateTime now) {
        change.setStatus(status);
        change.setResultMessage(message);
        if ("APPLIED".equals(status)) change.setAppliedAt(now);
        change.setUpdateTime(now);
        changeMapper.updateById(change);
    }

    /**
     * 根据锁定后的指针组装当前生产配置。
     * @param pointer 唯一生产指针
     * @return 当前配置；损坏状态返回 null 供调用方显式呈现
     */
    private AssistantProductionConfigDTO current(AssistantProductionPointer pointer) {
        if (pointer == null) return null;
        AssistantProductionConfig config = configMapper.selectById(pointer.getActiveConfigId());
        if (config == null) return null;
        AssistantWorkflowVersion workflow = workflow(config.getWorkflowVersion());
        return new AssistantProductionConfigDTO(config.getProductionConfigVersion(),
                config.getWorkflowVersion(), workflow == null ? null : workflow.getName(),
                config.getPromptVersion(), config.getModelExecutionConfigVersion(),
                config.getToolsetVersion(), config.getRagMode(), config.getRagIndexVersion(),
                workflow == null ? null : workflow.getImpactScope(), true, pointer.getRevision(),
                pointer.getActivatedBy(), pointer.getActivatedAt(), pointer.getObservationUntil());
    }

    /**
     * 查询配置引用的工作流发布项。
     * @param version 工作流版本
     * @return 工作流发布项或 null
     */
    private AssistantWorkflowVersion workflow(String version) {
        return workflowMapper.selectOne(new LambdaQueryWrapper<AssistantWorkflowVersion>()
                .eq(AssistantWorkflowVersion::getWorkflowVersion, version));
    }
}
