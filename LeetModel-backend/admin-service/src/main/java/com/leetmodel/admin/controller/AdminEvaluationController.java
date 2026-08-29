package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.dto.AdminEvaluationScoreRecalculateDTO;
import com.leetmodel.admin.dto.AdminEvaluationWeightSchemeCreateDTO;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskControlDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.api.dto.EvaluationScoreResultDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.feign.EvaluationFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 固定测试集与 AI 评审版本评价的管理入口。 */
@Validated
@RestController
@RequestMapping("/api/admin/ai/evaluations")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminEvaluationController {
    private final EvaluationFeignClient evaluationClient;
    private final ReviewFeignClient reviewClient;
    private final AssistantFeignClient assistantClient;
    private final AdminFeignExecutor executor;

    @Operation(summary = "查询可评价的AI功能与版本")
    @GetMapping("/features")
    public Result<List<AiFeatureDefinitionDTO>> features() {
        Result<AiFeatureDefinitionDTO> feature = executor.forward(
                "AI 评审服务", reviewClient::getFeatureDefinition);
        if (!feature.isSuccess()) {
            return Result.fail(feature.getCode(), feature.getMessage());
        }
        Result<AiFeatureDefinitionDTO> assistant = executor.forward(
                "AI 客服服务", assistantClient::getFeatureDefinition);
        if (!assistant.isSuccess()) {
            return Result.fail(assistant.getCode(), assistant.getMessage());
        }
        return Result.ok(List.of(feature.getData(), assistant.getData()));
    }

    @Operation(summary = "创建固定评价数据集")
    @PostMapping("/datasets")
    public Result<EvaluationDatasetDTO> createDataset(
            @Valid @RequestBody EvaluationDatasetCreateDTO request) {
        request.setCreatedBy(UserContext.getUserId());
        return executor.forward("质量评价服务", () -> evaluationClient.createDataset(request));
    }

    @Operation(summary = "查询固定评价数据集")
    @GetMapping("/datasets")
    public Result<List<EvaluationDatasetDTO>> datasets() {
        return executor.forward("质量评价服务", evaluationClient::listDatasets);
    }

    @Operation(summary = "预估评价任务规模与费用")
    @PostMapping("/estimates")
    public Result<EvaluationEstimateDTO> estimate(
            @Valid @RequestBody EvaluationEstimateRequestDTO request) {
        return executor.forward("质量评价服务", () -> evaluationClient.estimate(request));
    }

    @Operation(summary = "创建评价任务")
    @PostMapping("/tasks")
    public Result<EvaluationTaskDTO> createTask(@Valid @RequestBody EvaluationTaskCreateDTO request) {
        return executor.forward("质量评价服务", () -> evaluationClient.createTask(request));
    }

    @Operation(summary = "查询最近评价任务")
    @GetMapping("/tasks")
    public Result<List<EvaluationTaskSummaryDTO>> tasks(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("质量评价服务", () -> evaluationClient.listRecent(limit));
    }

    @Operation(summary = "查询评价任务详情")
    @GetMapping("/tasks/{taskId}")
    public Result<EvaluationTaskDTO> task(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.getTask(taskId));
    }

    @Operation(summary = "重试评价任务失败项")
    @PostMapping("/tasks/{taskId}/retry")
    public Result<EvaluationTaskDTO> retry(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.retry(taskId));
    }

    @Operation(summary = "暂停评价任务")
    @PostMapping("/tasks/{taskId}/pause")
    public Result<EvaluationTaskDTO> pause(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.pause(taskId,
                new EvaluationTaskControlDTO(UserContext.getUserId())));
    }

    @Operation(summary = "恢复评价任务")
    @PostMapping("/tasks/{taskId}/resume")
    public Result<EvaluationTaskDTO> resume(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.resume(taskId,
                new EvaluationTaskControlDTO(UserContext.getUserId())));
    }

    @Operation(summary = "取消评价任务")
    @PostMapping("/tasks/{taskId}/cancel")
    public Result<EvaluationTaskDTO> cancel(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.cancel(taskId,
                new EvaluationTaskControlDTO(UserContext.getUserId())));
    }

    @Operation(summary = "创建版本化权重方案")
    @PostMapping("/weight-schemes")
    public Result<EvaluationWeightSchemeDTO> createWeightScheme(
            @Valid @RequestBody AdminEvaluationWeightSchemeCreateDTO request) {
        return executor.forward("质量评价服务", () -> evaluationClient.createWeightScheme(
                request.toInternal(UserContext.getUserId())));
    }

    @Operation(summary = "查询版本化权重方案")
    @GetMapping("/weight-schemes")
    public Result<List<EvaluationWeightSchemeDTO>> weightSchemes(
            @RequestParam(required = false)
            @Size(max = 32)
            @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String featureCode,
            @RequestParam(required = false)
            @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
        return executor.forward("质量评价服务",
                () -> evaluationClient.listWeightSchemes(featureCode, status));
    }

    @Operation(summary = "停用权重方案")
    @PostMapping("/weight-schemes/{schemeId}/deactivate")
    public Result<EvaluationWeightSchemeDTO> deactivateWeightScheme(
            @PathVariable @Positive Long schemeId) {
        return executor.forward("质量评价服务",
                () -> evaluationClient.deactivateWeightScheme(schemeId,
                        new EvaluationTaskControlDTO(UserContext.getUserId())));
    }

    @Operation(summary = "使用另一权重方案重算选择指数")
    @PostMapping("/tasks/{taskId}/score-results/recalculate")
    public Result<EvaluationScoreResultDTO> recalculateScore(
            @PathVariable @Positive Long taskId,
            @Valid @RequestBody AdminEvaluationScoreRecalculateDTO request) {
        return executor.forward("质量评价服务", () -> evaluationClient.recalculateScore(
                taskId, request.toInternal(UserContext.getUserId())));
    }

    @Operation(summary = "比较同口径评价结果")
    @GetMapping("/comparisons")
    public Result<EvaluationComparisonDTO> compare(
            @RequestParam @Positive Long datasetId,
            @RequestParam @Min(1) @Max(100) Integer repeatCount) {
        return executor.forward("质量评价服务",
                () -> evaluationClient.compare(datasetId, repeatCount));
    }
}
