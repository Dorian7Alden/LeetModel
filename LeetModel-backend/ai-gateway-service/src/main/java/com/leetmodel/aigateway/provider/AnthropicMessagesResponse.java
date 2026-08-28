package com.leetmodel.aigateway.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.ai.model.AiMetricCompleteness;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record AnthropicMessagesResponse(
        String id,
        String model,
        String stopReason,
        List<ContentBlock> content,
        Usage usage
) {
    String outputText() {
        if (content == null) return null;
        return content.stream().filter(Objects::nonNull)
                .filter(block -> "text".equals(block.type()))
                .map(ContentBlock::text).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining());
    }

    String reasoningText() {
        if (content == null) return null;
        String value = content.stream().filter(Objects::nonNull)
                .filter(block -> "thinking".equals(block.type()))
                .map(ContentBlock::thinking).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining());
        return value.isEmpty() ? null : value;
    }

    AiChatResponse toUnified(AiProvider provider) {
        Long cacheHit = usage.cacheReadInputTokens();
        Long cacheCreation = usage.cacheCreationInputTokens();
        Long reasoning = usage.outputTokensDetails() == null
                ? null : usage.outputTokensDetails().thinkingTokens();
        Long total = usage.inputTokens() == null || usage.outputTokens() == null
                ? null : usage.inputTokens() + usage.outputTokens();
        return new AiChatResponse(null, provider, model, id, outputText(), reasoningText(), stopReason,
                new AiUsage(usage.inputTokens(), usage.outputTokens(), reasoning, cacheHit,
                        cacheCreation, null, total, AiMetricCompleteness.COMPLETE));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text, String thinking) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Usage(Long inputTokens, Long outputTokens, Long cacheReadInputTokens,
                 Long cacheCreationInputTokens, OutputTokensDetails outputTokensDetails) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OutputTokensDetails(Long thinkingTokens) {}
}
