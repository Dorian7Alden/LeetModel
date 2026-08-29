package com.leetmodel.evaluation.controller;

import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskControlDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeCreateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.service.EvaluationService;
import com.leetmodel.evaluation.service.EvaluationEstimateService;
import com.leetmodel.evaluation.service.EvaluationWeightSchemeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
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

@Validated
@RestController
@RequestMapping("/internal/evaluations")
@RequiredArgsConstructor
public class InternalEvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationEstimateService estimateService;
    private final EvaluationWeightSchemeService weightSchemeService;

    @Operation(summary = "创建固定评价数据集")
    @PostMapping("/datasets")
    public Result<EvaluationDatasetDTO> createDataset(
            @Valid @RequestBody EvaluationDatasetCreateDTO request) {
        return Result.ok(evaluationService.createDataset(request));
    }

    @Operation(summary = "查询固定评价数据集")
    @GetMapping("/datasets")
    public Result<List<EvaluationDatasetDTO>> listDatasets() {
        return Result.ok(evaluationService.listDatasets());
    }

    @Operation(summary = "预估评价批次规模和调用量")
    @PostMapping("/estimates")
    public Result<EvaluationEstimateDTO> estimate(
            @Valid @RequestBody EvaluationEstimateRequestDTO request) {
        return Result.ok(estimateService.estimate(request));
    }

    @Operation(summary = "创建版本质量评价任务")
    @PostMapping("/tasks")
    public Result<EvaluationTaskDTO> createTask(
            @Valid @RequestBody EvaluationTaskCreateDTO request) {
        return Result.ok(evaluationService.createTask(request));
    }

    @Operation(summary = "查询版本质量评价任务")
    @GetMapping("/tasks/{taskId}")
    public Result<EvaluationTaskDTO> getTask(
            @PathVariable @Positive(message = "评价任务标识必须为正整数") Long taskId) {
        return Result.ok(evaluationService.getTask(taskId));
    }

    @Operation(summary = "重试环境失败的版本质量评价任务")
    @PostMapping("/tasks/{taskId}/retry")
    public Result<EvaluationTaskDTO> retry(
            @PathVariable @Positive(message = "评价任务标识必须为正整数") Long taskId) {
        return Result.ok(evaluationService.retry(taskId));
    }

    @Operation(summary = "暂停评价任务的新槽位派发")
    @PostMapping("/tasks/{taskId}/pause")
    public Result<EvaluationTaskDTO> pause(@PathVariable @Positive Long taskId,
                                           @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(evaluationService.pause(taskId, request.getOperatorId()));
    }

    @Operation(summary = "恢复评价任务剩余槽位")
    @PostMapping("/tasks/{taskId}/resume")
    public Result<EvaluationTaskDTO> resume(@PathVariable @Positive Long taskId,
                                            @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(evaluationService.resume(taskId, request.getOperatorId()));
    }

    @Operation(summary = "取消评价任务及可取消的排队调用")
    @PostMapping("/tasks/{taskId}/cancel")
    public Result<EvaluationTaskDTO> cancel(@PathVariable @Positive Long taskId,
                                            @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(evaluationService.cancel(taskId, request.getOperatorId()));
    }

    @Operation(summary = "查询最近版本质量评价任务")
    @GetMapping("/tasks")
    public Result<List<EvaluationTaskSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(evaluationService.listRecentTasks(limit));
    }

    @Operation(summary = "获取版本质量评价任务数量")
    @GetMapping("/tasks/count")
    public Result<Long> countTasks() {
        return Result.ok(evaluationService.countTasks());
    }

    @Operation(summary = "对比相同评价口径下的评审版本")
    @GetMapping("/comparisons")
    public Result<EvaluationComparisonDTO> compare(
            @RequestParam @Positive(message = "数据集标识必须为正整数") Long datasetId,
            @RequestParam @Min(value = 1, message = "重复次数不能小于1")
            @Max(value = 100, message = "重复次数不能超过100") Integer repeatCount) {
        return Result.ok(evaluationService.compare(datasetId, repeatCount));
    }

    @Operation(summary = "创建版本化权重方案")
    @PostMapping("/weight-schemes")
    public Result<EvaluationWeightSchemeDTO> createWeightScheme(
            @Valid @RequestBody EvaluationWeightSchemeCreateDTO request) {
        return Result.ok(weightSchemeService.create(request));
    }

    @Operation(summary = "查询版本化权重方案")
    @GetMapping("/weight-schemes")
    public Result<List<EvaluationWeightSchemeDTO>> listWeightSchemes(
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String status) {
        return Result.ok(weightSchemeService.list(featureCode, status));
    }

    @Operation(summary = "停用版本化权重方案")
    @PostMapping("/weight-schemes/{schemeId}/deactivate")
    public Result<EvaluationWeightSchemeDTO> deactivateWeightScheme(
            @PathVariable @Positive(message = "权重方案标识必须为正整数") Long schemeId,
            @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(weightSchemeService.deactivate(schemeId, request.getOperatorId()));
    }
}
