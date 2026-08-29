package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "ai-evaluation-service")
public interface EvaluationFeignClient {

    @PostMapping("/internal/evaluations/datasets")
    Result<EvaluationDatasetDTO> createDataset(@RequestBody EvaluationDatasetCreateDTO request);

    @GetMapping("/internal/evaluations/datasets")
    Result<List<EvaluationDatasetDTO>> listDatasets();

    @PostMapping("/internal/evaluations/estimates")
    Result<EvaluationEstimateDTO> estimate(@RequestBody EvaluationEstimateRequestDTO request);

    @PostMapping("/internal/evaluations/tasks")
    Result<EvaluationTaskDTO> createTask(@RequestBody EvaluationTaskCreateDTO request);

    @GetMapping("/internal/evaluations/tasks/{taskId}")
    Result<EvaluationTaskDTO> getTask(@PathVariable("taskId") Long taskId);

    @PostMapping("/internal/evaluations/tasks/{taskId}/retry")
    Result<EvaluationTaskDTO> retry(@PathVariable("taskId") Long taskId);

    @GetMapping("/internal/evaluations/tasks")
    Result<List<EvaluationTaskSummaryDTO>> listRecent(@RequestParam("limit") Integer limit);

    @GetMapping("/internal/evaluations/tasks/count")
    Result<Long> countTasks();

    @GetMapping("/internal/evaluations/comparisons")
    Result<EvaluationComparisonDTO> compare(@RequestParam("datasetId") Long datasetId,
                                            @RequestParam("repeatCount") Integer repeatCount);
}
