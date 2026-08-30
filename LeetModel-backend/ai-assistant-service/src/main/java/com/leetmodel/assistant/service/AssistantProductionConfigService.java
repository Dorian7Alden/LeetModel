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
import com.leetmodel.assistant.workflow.AssistantProductionSnapshot;
import com.leetmodel.common.api.dto.AssistantProductionAuditDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangeApplyDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewRequestDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangeResultDTO;
import com.leetmodel.common.api.dto.AssistantProductionConfigDTO;
import com.leetmodel.common.api.dto.AssistantProductionWorkflowDTO;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** AI 客服生产版本目录、当前配置、预览和历史查询的唯一业务所有者。 */
@Service
public class AssistantProductionConfigService {

    private static final long POINTER_ID = 1L;

    private final AssistantWorkflowVersionMapper workflowMapper;
    private final AssistantProductionConfigMapper configMapper;
    private final AssistantProductionPointerMapper pointerMapper;
    private final AssistantProductionChangeRequestMapper changeMapper;
    private final AssistantProductionAuditMapper auditMapper;
    private final AssistantProductionDependencyValidator dependencyValidator;
    private final AssistantProductionChangeTransactionService transactionService;

    public AssistantProductionConfigService(
            AssistantWorkflowVersionMapper workflowMapper,
            AssistantProductionConfigMapper configMapper,
            AssistantProductionPointerMapper pointerMapper,
            AssistantProductionChangeRequestMapper changeMapper,
            AssistantProductionAuditMapper auditMapper,
            AssistantProductionDependencyValidator dependencyValidator,
            AssistantProductionChangeTransactionService transactionService) {
        this.workflowMapper = workflowMapper;
        this.configMapper = configMapper;
        this.pointerMapper = pointerMapper;
        this.changeMapper = changeMapper;
        this.auditMapper = auditMapper;
        this.dependencyValidator = dependencyValidator;
        this.transactionService = transactionService;
    }

    /**
     * 查询可发现的客服工作流发布目录。
     * @return 工作流发布项
     */
    public List<AssistantProductionWorkflowDTO> listWorkflows() {
        return workflowMapper.selectList(new LambdaQueryWrapper<AssistantWorkflowVersion>()
                        .orderByAsc(AssistantWorkflowVersion::getId))
                .stream().map(this::toWorkflow).toList();
    }

    /**
     * 查询当前生效的客服生产配置。
     * @return 当前生产配置及指针信息
     */
    public AssistantProductionConfigDTO current() {
        State state = requiredState();
        return toConfig(state.config(), state.workflow(), state.pointer());
    }

    /**
     * 读取新客服回复需要持久化的完整生产快照。
     * @return 不可变执行快照
     */
    public AssistantProductionSnapshot currentSnapshot() {
        State state = requiredState();
        return new AssistantProductionSnapshot(state.config().getProductionConfigVersion(),
                state.pointer().getRevision(), state.config().getWorkflowVersion(),
                state.config().getPromptVersion(),
                state.config().getModelExecutionConfigVersion(), state.config().getRagMode(),
                state.config().getRagIndexVersion());
    }

    /**
     * 查询最近创建的不可变生产配置。
     * @param limit 最大返回数量
     * @return 生产配置列表
     */
    public List<AssistantProductionConfigDTO> listConfigs(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        State current = requiredState();
        return configMapper.selectList(new LambdaQueryWrapper<AssistantProductionConfig>()
                        .orderByDesc(AssistantProductionConfig::getCreateTime)
                        .last("LIMIT " + safeLimit))
                .stream().map(config -> {
                    AssistantWorkflowVersion workflow = requiredWorkflow(config.getWorkflowVersion());
                    return toConfig(config, workflow,
                            config.getId().equals(current.config().getId()) ? current.pointer() : null);
                }).toList();
    }

    /**
     * 查询最近成功生效的生产变更审计。
     * @param limit 最大返回数量
     * @return 成功变更审计列表
     */
    public List<AssistantProductionAuditDTO> listAudits(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return auditMapper.selectList(new LambdaQueryWrapper<AssistantProductionAudit>()
                        .orderByDesc(AssistantProductionAudit::getChangedAt)
                        .last("LIMIT " + safeLimit))
                .stream().map(audit -> new AssistantProductionAuditDTO(audit.getId(),
                        audit.getChangeRequestId(), audit.getAction(),
                        configMapper.selectById(audit.getFromConfigId()).getProductionConfigVersion(),
                        configMapper.selectById(audit.getToConfigId()).getProductionConfigVersion(),
                        audit.getFromRevision(), audit.getToRevision(), audit.getOperatorId(),
                        audit.getReason(), audit.getChangedAt())).toList();
    }

    /**
     * 校验目标依赖并冻结一次短时有效的生产变更预览。
     * @param request 带操作者、原因和期望 revision 的变更命令
     * @return 服务端冻结的变更差异
     */
    public AssistantProductionChangePreviewDTO preview(
            AssistantProductionChangePreviewRequestDTO request) {
        // 校验不能由页面补救的服务端业务约束
        BusinessException.throwIf(request.getOperatorId() == null || request.getOperatorId() <= 0,
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        BusinessException.throwIf(!"ACTIVATE".equals(request.getAction())
                        && !"ROLLBACK".equals(request.getAction()),
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        BusinessException.throwIf(reason.length() < 10 || reason.length() > 500,
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        request.setReason(reason);
        // 锁定预览所依据的当前生产状态
        State state = requiredState();
        BusinessException.throwIf(!state.pointer().getRevision().equals(request.getExpectedRevision()),
                AssistantErrorCode.PRODUCTION_CHANGE_CONFLICT);

        // 解析不可变目标并完成跨服务与物理索引预检查
        AssistantProductionConfig target = "ACTIVATE".equals(request.getAction())
                ? activationTarget(request) : rollbackTarget(request);
        BusinessException.throwIf(target.getId().equals(state.config().getId()),
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        assertDependenciesReady(target);

        // 保存第二次确认唯一可以引用的服务端变更请求
        LocalDateTime now = LocalDateTime.now();
        AssistantProductionChangeRequest change = new AssistantProductionChangeRequest();
        change.setChangeRequestId(UUID.randomUUID().toString().replace("-", ""));
        change.setAction(request.getAction());
        change.setExpectedRevision(request.getExpectedRevision());
        change.setSourceConfigId(state.config().getId());
        change.setTargetConfigId(target.getId());
        change.setOperatorId(request.getOperatorId());
        change.setReason(reason);
        change.setStatus("PENDING");
        change.setExpiresAt(now.plusMinutes(10));
        change.setCreateTime(now);
        change.setUpdateTime(now);
        changeMapper.insert(change);

        AssistantWorkflowVersion targetWorkflow = requiredWorkflow(target.getWorkflowVersion());
        return new AssistantProductionChangePreviewDTO(change.getChangeRequestId(),
                change.getAction(), change.getStatus(), change.getExpectedRevision(),
                toConfig(state.config(), state.workflow(), state.pointer()),
                toConfig(target, targetWorkflow, null), differences(state.config(), target),
                targetWorkflow.getImpactScope(), change.getReason(), change.getExpiresAt());
    }

    /**
     * 重检目标依赖并原子确认已冻结的生产变更。
     * @param request 只包含变更请求标识和登录操作者的确认命令
     * @return 生效、冲突、拒绝或过期结果
     */
    public AssistantProductionChangeResultDTO apply(AssistantProductionChangeApplyDTO request) {
        // 在事务外读取目标并执行可能较慢的外部依赖检查
        AssistantProductionChangeRequest change = changeMapper.selectOne(
                new LambdaQueryWrapper<AssistantProductionChangeRequest>()
                        .eq(AssistantProductionChangeRequest::getChangeRequestId,
                                request.getChangeRequestId()));
        BusinessException.throwIf(change == null
                        || !request.getOperatorId().equals(change.getOperatorId()),
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        boolean ready = false;
        if ("PENDING".equals(change.getStatus())
                && change.getExpiresAt().isAfter(LocalDateTime.now())) {
            try {
                ready = dependencyValidator.isReady(
                        configMapper.selectById(change.getTargetConfigId()));
            } catch (RuntimeException ignored) {
                ready = false;
            }
        }
        // 本地事务重新锁定请求并完成条件更新和审计
        return transactionService.apply(request.getChangeRequestId(),
                request.getOperatorId(), ready);
    }

    /**
     * 查找或创建激活动作引用的不可变生产配置。
     * @param request 激活预览请求
     * @return 激活目标配置
     */
    private AssistantProductionConfig activationTarget(
            AssistantProductionChangePreviewRequestDTO request) {
        BusinessException.throwIf(request.getTargetWorkflowVersion() == null
                        || request.getTargetProductionConfigVersion() != null,
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        AssistantWorkflowVersion workflow = requiredWorkflow(request.getTargetWorkflowVersion());
        BusinessException.throwIf(!"ENABLED".equals(workflow.getStatus()),
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        String ragIndex = normalizeRagIndex(workflow, request.getRagIndexVersion());
        String ragKey = ragIndex == null ? "NONE" : ragIndex;
        AssistantProductionConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<AssistantProductionConfig>()
                        .eq(AssistantProductionConfig::getWorkflowVersion,
                                workflow.getWorkflowVersion())
                        .eq(AssistantProductionConfig::getRagIndexKey, ragKey));
        if (existing != null) return existing;

        AssistantProductionConfig created = new AssistantProductionConfig();
        created.setProductionConfigVersion("ASSISTANT_PROD_CFG_"
                + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase());
        created.setWorkflowVersion(workflow.getWorkflowVersion());
        created.setPromptVersion(workflow.getPromptVersion());
        created.setModelExecutionConfigVersion(workflow.getModelExecutionConfigVersion());
        created.setRagMode(workflow.getRagMode());
        created.setRagIndexVersion(ragIndex);
        created.setRagIndexKey(ragKey);
        created.setCreatedBy(request.getOperatorId());
        created.setReason(request.getReason().trim());
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(created.getCreateTime());
        try {
            configMapper.insert(created);
            return created;
        } catch (DuplicateKeyException exception) {
            return configMapper.selectOne(new LambdaQueryWrapper<AssistantProductionConfig>()
                    .eq(AssistantProductionConfig::getWorkflowVersion,
                            workflow.getWorkflowVersion())
                    .eq(AssistantProductionConfig::getRagIndexKey, ragKey));
        }
    }

    /**
     * 校验并读取曾经生效过的回滚目标。
     * @param request 回滚预览请求
     * @return 历史生产配置
     */
    private AssistantProductionConfig rollbackTarget(
            AssistantProductionChangePreviewRequestDTO request) {
        BusinessException.throwIf(request.getTargetProductionConfigVersion() == null
                        || request.getTargetWorkflowVersion() != null
                        || request.getRagIndexVersion() != null,
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        AssistantProductionConfig target = configMapper.selectOne(
                new LambdaQueryWrapper<AssistantProductionConfig>()
                        .eq(AssistantProductionConfig::getProductionConfigVersion,
                                request.getTargetProductionConfigVersion()));
        BusinessException.throwIf(target == null || !wasActive(target.getId()),
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        BusinessException.throwIf(!"ENABLED".equals(
                        requiredWorkflow(target.getWorkflowVersion()).getStatus()),
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        return target;
    }

    /**
     * 判断配置是否曾经作为生产配置生效。
     * @param configId 配置主键
     * @return 默认配置或审计中出现过时为 true
     */
    private boolean wasActive(Long configId) {
        return Long.valueOf(1L).equals(configId) || auditMapper.selectCount(
                new LambdaQueryWrapper<AssistantProductionAudit>()
                        .eq(AssistantProductionAudit::getToConfigId, configId)) > 0;
    }

    /**
     * 按工作流 RAG 模式规范化物理索引版本。
     * @param workflow 目标工作流
     * @param supplied 请求提供的索引版本
     * @return NONE 模式返回 null，固定索引模式返回去空白版本
     */
    private String normalizeRagIndex(AssistantWorkflowVersion workflow, String supplied) {
        if ("NONE".equals(workflow.getRagMode())) {
            BusinessException.throwIf(supplied != null,
                    AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
            return null;
        }
        BusinessException.throwIf(!"FIXED_INDEX".equals(workflow.getRagMode())
                        || supplied == null || supplied.isBlank(),
                AssistantErrorCode.PRODUCTION_CHANGE_INVALID);
        return supplied.trim();
    }

    /**
     * 将依赖检查异常统一转换为不暴露内部信息的业务错误。
     * @param target 待激活或回滚的目标配置
     */
    private void assertDependenciesReady(AssistantProductionConfig target) {
        try {
            BusinessException.throwIf(!dependencyValidator.isReady(target),
                    AssistantErrorCode.PRODUCTION_DEPENDENCY_UNAVAILABLE);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(AssistantErrorCode.PRODUCTION_DEPENDENCY_UNAVAILABLE);
        }
    }

    /**
     * 读取并校验唯一生产指针引用的完整状态。
     * @return 指针、配置和工作流组合
     */
    private State requiredState() {
        AssistantProductionPointer pointer = pointerMapper.selectById(POINTER_ID);
        BusinessException.throwIf(pointer == null,
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        AssistantProductionConfig config = configMapper.selectById(pointer.getActiveConfigId());
        BusinessException.throwIf(config == null,
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        return new State(pointer, config, requiredWorkflow(config.getWorkflowVersion()));
    }

    /**
     * 查询必须存在的工作流发布项。
     * @param workflowVersion 工作流版本
     * @return 工作流发布项
     */
    private AssistantWorkflowVersion requiredWorkflow(String workflowVersion) {
        AssistantWorkflowVersion workflow = workflowMapper.selectOne(
                new LambdaQueryWrapper<AssistantWorkflowVersion>()
                        .eq(AssistantWorkflowVersion::getWorkflowVersion, workflowVersion));
        BusinessException.throwIf(workflow == null,
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        return workflow;
    }

    /**
     * 转换工作流发布项为内部接口 DTO。
     * @param workflow 工作流实体
     * @return 工作流 DTO
     */
    private AssistantProductionWorkflowDTO toWorkflow(AssistantWorkflowVersion workflow) {
        return new AssistantProductionWorkflowDTO(workflow.getWorkflowVersion(),
                workflow.getName(), workflow.getStatus(), workflow.getPromptVersion(),
                workflow.getModelExecutionConfigVersion(), workflow.getRagMode(),
                workflow.getInputSchema(), workflow.getOutputSchema(),
                workflow.getCompatibility(), workflow.getImpactScope(),
                workflow.getExperimentCandidate());
    }

    /**
     * 转换生产配置，并仅为当前项附加指针状态。
     * @param config 不可变生产配置
     * @param workflow 对应工作流发布项
     * @param pointer 当前指针，历史项传 null
     * @return 生产配置 DTO
     */
    private AssistantProductionConfigDTO toConfig(AssistantProductionConfig config,
                                                  AssistantWorkflowVersion workflow,
                                                  AssistantProductionPointer pointer) {
        boolean everActive = pointer != null || auditMapper.selectCount(
                new LambdaQueryWrapper<AssistantProductionAudit>()
                        .eq(AssistantProductionAudit::getFromConfigId, config.getId())
                        .or()
                        .eq(AssistantProductionAudit::getToConfigId, config.getId())) > 0;
        return new AssistantProductionConfigDTO(config.getProductionConfigVersion(),
                config.getWorkflowVersion(), workflow.getName(), config.getPromptVersion(),
                config.getModelExecutionConfigVersion(), config.getRagMode(),
                config.getRagIndexVersion(), workflow.getImpactScope(),
                everActive,
                pointer == null ? null : pointer.getRevision(),
                pointer == null ? null : pointer.getActivatedBy(),
                pointer == null ? null : pointer.getActivatedAt(),
                pointer == null ? null : pointer.getObservationUntil());
    }

    /**
     * 生成页面二次确认使用的字段差异摘要。
     * @param current 当前生产配置
     * @param target 目标生产配置
     * @return 有变化的字段说明
     */
    private List<String> differences(AssistantProductionConfig current,
                                     AssistantProductionConfig target) {
        List<String> differences = new ArrayList<>();
        addDifference(differences, "工作流版本", current.getWorkflowVersion(),
                target.getWorkflowVersion());
        addDifference(differences, "Prompt版本", current.getPromptVersion(),
                target.getPromptVersion());
        addDifference(differences, "模型执行配置", current.getModelExecutionConfigVersion(),
                target.getModelExecutionConfigVersion());
        addDifference(differences, "RAG模式", current.getRagMode(), target.getRagMode());
        addDifference(differences, "RAG索引", current.getRagIndexVersion(),
                target.getRagIndexVersion());
        return List.copyOf(differences);
    }

    /**
     * 在字段值变化时追加一条差异。
     * @param differences 差异集合
     * @param label 字段展示名
     * @param from 旧值
     * @param to 新值
     */
    private void addDifference(List<String> differences, String label,
                               String from, String to) {
        if (!java.util.Objects.equals(from, to)) {
            differences.add(label + "：" + display(from) + " → " + display(to));
        }
    }

    /**
     * 将可空配置值转换为页面可读文本。
     * @param value 配置值
     * @return 页面展示值
     */
    private String display(String value) {
        return value == null ? "不适用" : value;
    }

    private record State(AssistantProductionPointer pointer,
                         AssistantProductionConfig config,
                         AssistantWorkflowVersion workflow) {
    }
}
