package com.leetmodel.common.api.feign;

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
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

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

    @GetMapping("/internal/assistant/conversations/production/workflows")
    Result<List<AssistantProductionWorkflowDTO>> listProductionWorkflows();

    @GetMapping("/internal/assistant/conversations/production/current")
    Result<AssistantProductionConfigDTO> getCurrentProductionConfig();

    @GetMapping("/internal/assistant/conversations/production/configs")
    Result<List<AssistantProductionConfigDTO>> listProductionConfigs(
            @RequestParam("limit") Integer limit);

    @GetMapping("/internal/assistant/conversations/production/audits")
    Result<List<AssistantProductionAuditDTO>> listProductionAudits(
            @RequestParam("limit") Integer limit);

    @PostMapping("/internal/assistant/conversations/production/changes/preview")
    Result<AssistantProductionChangePreviewDTO> previewProductionChange(
            @RequestBody AssistantProductionChangePreviewRequestDTO request);

    @PostMapping("/internal/assistant/conversations/production/changes/apply")
    Result<AssistantProductionChangeResultDTO> applyProductionChange(
            @RequestBody AssistantProductionChangeApplyDTO request);
}
