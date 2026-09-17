package com.leetmodel.aigateway.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** OpenAI-compatible /embeddings 响应。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiEmbeddingResponse(
        String id,
        String model,
        List<Item> data,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(int index, List<Float> embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Long promptTokens,
            @JsonProperty("total_tokens") Long totalTokens
    ) {
    }
}
