package com.leetmodel.aigateway.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiUsage;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record OpenAiResponsesResponse(
        String id,
        String model,
        String status,
        List<OutputItem> output,
        Usage usage,
        IncompleteDetails incompleteDetails
) {
    String outputText() {
        if (output == null) return null;
        return output.stream().filter(Objects::nonNull)
                .flatMap(item -> item.content() == null ? java.util.stream.Stream.empty() : item.content().stream())
                .filter(part -> "output_text".equals(part.type()))
                .map(OutputContent::text).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining());
    }

    AiChatResponse toUnified(AiProvider provider) {
        Long cached = usage.inputTokensDetails() == null ? null : usage.inputTokensDetails().cachedTokens();
        Long cacheMiss = cached == null || usage.inputTokens() == null
                ? null : Math.max(0, usage.inputTokens() - cached);
        Long reasoning = usage.outputTokensDetails() == null
                ? null : usage.outputTokensDetails().reasoningTokens();
        String finishReason = incompleteDetails == null || incompleteDetails.reason() == null
                ? status : incompleteDetails.reason();
        return new AiChatResponse(null, provider, model, id, outputText(), null, finishReason,
                new AiUsage(usage.inputTokens(), cached, cacheMiss, usage.outputTokens(), reasoning,
                        usage.totalTokens(), true));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputItem(String type, List<OutputContent> content) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputContent(String type, String text) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Usage(Long inputTokens, Long outputTokens, Long totalTokens,
                 InputTokensDetails inputTokensDetails, OutputTokensDetails outputTokensDetails) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record InputTokensDetails(Long cachedTokens) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OutputTokensDetails(Long reasoningTokens) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IncompleteDetails(String reason) {}
}
