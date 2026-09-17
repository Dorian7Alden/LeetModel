package com.leetmodel.assistant.controller;

import com.leetmodel.assistant.service.AssistantService;
import com.leetmodel.assistant.service.AssistantExperimentService;
import com.leetmodel.assistant.service.AssistantProductionConfigService;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.api.dto.AssistantProductionAuditDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangeApplyDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewRequestDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangeResultDTO;
import com.leetmodel.common.api.dto.AssistantProductionConfigDTO;
import com.leetmodel.common.api.dto.AssistantProductionWorkflowDTO;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/assistant/conversations")
@RequiredArgsConstructor
public class InternalAssistantController {

    private final AssistantService assistantService;
    private final AssistantExperimentService experimentService;
    private final AssistantProductionConfigService productionConfigService;

    /**
     * 统计系统全量客服会话总数。
     *
     * @return 会话记录总数
     */
    @Operation(summary = "获取 AI 客服会话数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(assistantService.countConversations());
    }

    /**
     * 按时间倒序查询最近创建的客服会话摘要列表。
     *
     * @param limit 单次拉取数量上限
     * @return 会话摘要 DTO 列表
     */
    @Operation(summary = "获取最近 AI 客服会话")
    @GetMapping
    public Result<List<AssistantConversationSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(assistantService.listRecent(limit));
    }

    /**
     * 查询客服助手可供质量评测的版本定义与工作流清单。
     *
     * @return 特征定义 DTO
     */
    @Operation(summary = "查询 AI 客服可评价版本")
    @GetMapping("/feature-definition")
    public Result<AiFeatureDefinitionDTO> featureDefinition() {
        return Result.ok(experimentService.featureDefinition());
    }

    /**
     * 在隔离沙箱中执行单轮对话实验（用于自动化评测质量对比）。
     *
     * @param request 实验参数对象，不能为 null
     * @return 实验评测结果 DTO
     */
    @Operation(summary = "执行客服单轮隔离实验")
    @PostMapping("/experiments")
    public Result<AiExperimentResultDTO> runExperiment(
            @Valid @RequestBody AiExperimentRequestDTO request) {
        return Result.ok(experimentService.run(request));
    }

    /**
     * 查询 AI 客服支持的全部候选生产工作流目录。
     *
     * @return 生产工作流 DTO 列表
     */
    @Operation(summary = "查询AI客服生产工作流目录")
    @GetMapping("/production/workflows")
    public Result<List<AssistantProductionWorkflowDTO>> productionWorkflows() {
        return Result.ok(productionConfigService.listWorkflows());
    }

    /**
     * 查询当前正在生效的 AI 客服生产配置快照。
     *
     * @return 当前生产配置 DTO
     */
    @Operation(summary = "查询AI客服当前生产配置")
    @GetMapping("/production/current")
    public Result<AssistantProductionConfigDTO> currentProductionConfig() {
        return Result.ok(productionConfigService.current());
    }

    /**
     * 按时间倒序查询不可变的生产配置历史版本列表。
     *
     * @param limit 单次拉取数量上限
     * @return 生产配置历史 DTO 列表
     */
    @Operation(summary = "查询AI客服不可变生产配置")
    @GetMapping("/production/configs")
    public Result<List<AssistantProductionConfigDTO>> productionConfigs(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return Result.ok(productionConfigService.listConfigs(limit));
    }

    /**
     * 查询生产工作流切换变更的审计流水记录。
     *
     * @param limit 单次拉取数量上限
     * @return 生产变更审计 DTO 列表
     */
    @Operation(summary = "查询AI客服生产变更审计")
    @GetMapping("/production/audits")
    public Result<List<AssistantProductionAuditDTO>> productionAudits(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return Result.ok(productionConfigService.listAudits(limit));
    }

    /**
     * 预览待执行的生产配置变更并生成临时冻结快照。
     *
     * @param request 变更预览请求对象，不能为 null
     * @return 变更预览 DTO
     */
    @Operation(summary = "预览并冻结AI客服生产配置变更")
    @PostMapping("/production/changes/preview")
    public Result<AssistantProductionChangePreviewDTO> previewProductionChange(
            @Valid @RequestBody AssistantProductionChangePreviewRequestDTO request) {
        return Result.ok(productionConfigService.preview(request));
    }

    /**
     * 管理员正式应用确认此前已预览冻结的生产配置变更。
     *
     * @param request 变更确认应用对象，不能为 null
     * @return 变更结果 DTO
     */
    @Operation(summary = "确认AI客服生产配置变更")
    @PostMapping("/production/changes/apply")
    public Result<AssistantProductionChangeResultDTO> applyProductionChange(
            @Valid @RequestBody AssistantProductionChangeApplyDTO request) {
        return Result.ok(productionConfigService.apply(request));
    }
}
