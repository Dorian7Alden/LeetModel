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

    @Operation(summary = "获取 AI 客服会话数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(assistantService.countConversations());
    }

    @Operation(summary = "获取最近 AI 客服会话")
    @GetMapping
    public Result<List<AssistantConversationSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(assistantService.listRecent(limit));
    }

    @Operation(summary = "查询 AI 客服可评价版本")
    @GetMapping("/feature-definition")
    public Result<AiFeatureDefinitionDTO> featureDefinition() {
        return Result.ok(experimentService.featureDefinition());
    }

    @Operation(summary = "执行客服单轮隔离实验")
    @PostMapping("/experiments")
    public Result<AiExperimentResultDTO> runExperiment(
            @Valid @RequestBody AiExperimentRequestDTO request) {
        return Result.ok(experimentService.run(request));
    }

    @Operation(summary = "查询AI客服生产工作流目录")
    @GetMapping("/production/workflows")
    public Result<List<AssistantProductionWorkflowDTO>> productionWorkflows() {
        return Result.ok(productionConfigService.listWorkflows());
    }

    @Operation(summary = "查询AI客服当前生产配置")
    @GetMapping("/production/current")
    public Result<AssistantProductionConfigDTO> currentProductionConfig() {
        return Result.ok(productionConfigService.current());
    }

    @Operation(summary = "查询AI客服不可变生产配置")
    @GetMapping("/production/configs")
    public Result<List<AssistantProductionConfigDTO>> productionConfigs(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return Result.ok(productionConfigService.listConfigs(limit));
    }

    @Operation(summary = "查询AI客服生产变更审计")
    @GetMapping("/production/audits")
    public Result<List<AssistantProductionAuditDTO>> productionAudits(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return Result.ok(productionConfigService.listAudits(limit));
    }

    @Operation(summary = "预览并冻结AI客服生产配置变更")
    @PostMapping("/production/changes/preview")
    public Result<AssistantProductionChangePreviewDTO> previewProductionChange(
            @Valid @RequestBody AssistantProductionChangePreviewRequestDTO request) {
        return Result.ok(productionConfigService.preview(request));
    }

    @Operation(summary = "确认AI客服生产配置变更")
    @PostMapping("/production/changes/apply")
    public Result<AssistantProductionChangeResultDTO> applyProductionChange(
            @Valid @RequestBody AssistantProductionChangeApplyDTO request) {
        return Result.ok(productionConfigService.apply(request));
    }
}
