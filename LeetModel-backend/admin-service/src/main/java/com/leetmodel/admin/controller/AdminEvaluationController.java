package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.api.feign.EvaluationFeignClient;
import com.leetmodel.common.core.result.Result;
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

/** 固定测试集与 AI 评审版本评价的管理入口。 */
@Validated
@RestController
@RequestMapping("/api/admin/ai/evaluations")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminEvaluationController {
    private final EvaluationFeignClient evaluationClient;
    private final AdminFeignExecutor executor;

    @PostMapping("/datasets")
    public Result<EvaluationDatasetDTO> createDataset(
            @Valid @RequestBody EvaluationDatasetCreateDTO request) {
        return executor.forward("质量评价服务", () -> evaluationClient.createDataset(request));
    }

    @GetMapping("/datasets")
    public Result<List<EvaluationDatasetDTO>> datasets() {
        return executor.forward("质量评价服务", evaluationClient::listDatasets);
    }

    @PostMapping("/tasks")
    public Result<EvaluationTaskDTO> createTask(@Valid @RequestBody EvaluationTaskCreateDTO request) {
        return executor.forward("质量评价服务", () -> evaluationClient.createTask(request));
    }

    @GetMapping("/tasks")
    public Result<List<EvaluationTaskSummaryDTO>> tasks(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("质量评价服务", () -> evaluationClient.listRecent(limit));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<EvaluationTaskDTO> task(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.getTask(taskId));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public Result<EvaluationTaskDTO> retry(@PathVariable @Positive Long taskId) {
        return executor.forward("质量评价服务", () -> evaluationClient.retry(taskId));
    }

    @GetMapping("/comparisons")
    public Result<EvaluationComparisonDTO> compare(
            @RequestParam @Positive Long datasetId,
            @RequestParam @Min(1) @Max(3) Integer repeatCount) {
        return executor.forward("质量评价服务",
                () -> evaluationClient.compare(datasetId, repeatCount));
    }
}
