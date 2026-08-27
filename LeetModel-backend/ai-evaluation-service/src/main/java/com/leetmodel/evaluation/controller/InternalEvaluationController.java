package com.leetmodel.evaluation.controller;

import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.service.EvaluationService;
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
            @Max(value = 3, message = "重复次数不能超过3") Integer repeatCount) {
        return Result.ok(evaluationService.compare(datasetId, repeatCount));
    }
}
