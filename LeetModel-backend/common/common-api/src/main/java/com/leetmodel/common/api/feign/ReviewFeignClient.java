package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.ReviewExperimentRequestDTO;
import com.leetmodel.common.api.dto.ReviewExperimentResultDTO;
import com.leetmodel.common.api.dto.ReviewVersionDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "ai-review-service")
public interface ReviewFeignClient {
    @PostMapping("/internal/reviews/tasks")
    Result<Long> createTask(@RequestParam Long submissionId, @RequestParam Long teamId, @RequestParam Long problemId);

    @GetMapping("/internal/reviews/submissions/{submissionId}")
    Result<ReviewSummaryDTO> getBySubmission(@PathVariable Long submissionId);

    @GetMapping("/internal/reviews/completed")
    Result<List<ReviewSummaryDTO>> listCompleted(@RequestParam(required = false) Long problemId);

    @GetMapping("/internal/reviews/count")
    Result<Long> getReviewCount();

    @GetMapping("/internal/reviews/recent")
    Result<List<ReviewSummaryDTO>> listRecent(@RequestParam("limit") Integer limit);

    @PostMapping("/internal/reviews/experiments")
    Result<ReviewExperimentResultDTO> runExperiment(
            @RequestBody ReviewExperimentRequestDTO request);

    @GetMapping("/internal/reviews/versions")
    Result<List<ReviewVersionDTO>> listVersions();

    @GetMapping("/internal/reviews/feature-definition")
    Result<AiFeatureDefinitionDTO> getFeatureDefinition();
}
