package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * AI 客服服务内部调用契约。
 */
@FeignClient(name = "ai-assistant-service")
public interface AssistantFeignClient {

    @GetMapping("/internal/assistant/conversations/count")
    Result<Long> getConversationCount();

    @GetMapping("/internal/assistant/conversations")
    Result<List<AssistantConversationSummaryDTO>> listRecentConversations(
            @RequestParam("limit") Integer limit);

    @GetMapping("/internal/assistant/conversations/feature-definition")
    Result<AiFeatureDefinitionDTO> getFeatureDefinition();

    @PostMapping("/internal/assistant/conversations/experiments")
    Result<AiExperimentResultDTO> runExperiment(@RequestBody AiExperimentRequestDTO request);
}
