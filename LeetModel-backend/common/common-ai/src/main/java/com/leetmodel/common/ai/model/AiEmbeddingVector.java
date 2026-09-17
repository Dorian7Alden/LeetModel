package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/** 单个输入对应的 Embedding 向量。 */
public record AiEmbeddingVector(
        @PositiveOrZero int index,
        @NotEmpty List<@NotNull Float> values
) {
    public AiEmbeddingVector {
        if (values != null) values = List.copyOf(values);
    }

    @JsonIgnore
    @AssertTrue(message = "Embedding 向量只能包含有限数值")
    public boolean isFinite() {
        return values == null || values.stream().allMatch(value -> value != null && Float.isFinite(value));
    }
}
