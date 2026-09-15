package com.leetmodel.aigateway.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record OpenAiCompatibleStreamChunk(
        String id,
        String model,
        List<Choice> choices,
        OpenAiCompatibleResponse.Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Choice(
            Delta delta,
            String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Delta(
            String role,
            String content,
            String reasoningContent,
            List<ToolCall> toolCalls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ToolCall(
            Integer index,
            String id,
            String type,
            Function function
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Function(
            String name,
            String arguments
    ) {}
}
