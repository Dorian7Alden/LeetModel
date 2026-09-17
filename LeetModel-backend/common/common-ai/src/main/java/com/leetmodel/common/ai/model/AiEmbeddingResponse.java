package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;

/** 统一 Embedding 响应；不包含输入原文、渠道配置或密钥。 */
public record AiEmbeddingResponse(
        @NotBlank @Size(max = 64) String callId,
        @NotBlank @Size(max = 100) String logicalModel,
        @NotBlank @Size(max = 100) String model,
        @Positive int dimension,
        @NotEmpty List<@Valid AiEmbeddingVector> vectors,
        @Valid AiUsage usage,
        @Valid AiCost cost
) {
    public AiEmbeddingResponse {
        if (vectors != null) vectors = List.copyOf(vectors);
    }

    @JsonIgnore
    @AssertTrue(message = "Embedding 响应的索引或向量维度不一致")
    public boolean isShapeValid() {
        if (vectors == null || dimension <= 0) return true;
        HashSet<Integer> indexes = new HashSet<>();
        for (AiEmbeddingVector vector : vectors) {
            if (vector == null || vector.values() == null || vector.values().size() != dimension
                    || !indexes.add(vector.index())) return false;
        }
        return true;
    }
}
