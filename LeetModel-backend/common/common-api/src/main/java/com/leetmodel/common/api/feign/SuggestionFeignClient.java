package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 论文建议服务内部调用契约。
 */
@FeignClient(name = "ai-suggestion-service")
public interface SuggestionFeignClient {

    @GetMapping("/internal/suggestions/count")
    Result<Long> getSuggestionCount();

    @GetMapping("/internal/suggestions/tasks")
    Result<List<SuggestionTaskSummaryDTO>> listRecentTasks(@RequestParam Integer limit);

    @GetMapping("/internal/suggestions/feature-definition")
    Result<AiFeatureDefinitionDTO> getFeatureDefinition();

    @PostMapping("/internal/suggestions/experiments")
    Result<AiExperimentResultDTO> runExperiment(@RequestBody AiExperimentRequestDTO request);
}
