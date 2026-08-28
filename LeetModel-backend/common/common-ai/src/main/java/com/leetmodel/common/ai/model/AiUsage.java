package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.stream.Stream;

/** 统一 AI Token 用量；null 表示上游没有提供，明确的 0 保持为 0。 */
public record AiUsage(
        @PositiveOrZero Long inputTokens,
        @PositiveOrZero Long outputTokens,
        @PositiveOrZero Long reasoningTokens,
        @PositiveOrZero Long cacheHitTokens,
        @PositiveOrZero Long cacheCreationTokens,
        @PositiveOrZero Long cacheMissTokens,
        @PositiveOrZero Long totalTokens,
        AiMetricCompleteness completeness
) {
    @JsonIgnore
    @AssertTrue(message = "用量值与完整性不一致")
    public boolean isCompletenessValid() {
        if (completeness == null) return false;
        boolean any = Stream.of(inputTokens, outputTokens, reasoningTokens, cacheHitTokens,
                cacheCreationTokens, cacheMissTokens, totalTokens).anyMatch(value -> value != null);
        return switch (completeness) {
            case UNKNOWN -> !any;
            case PARTIAL -> any;
            case COMPLETE -> inputTokens != null && outputTokens != null && totalTokens != null;
        };
    }

    @JsonIgnore
    public boolean complete() {
        return completeness == AiMetricCompleteness.COMPLETE;
    }

    @Deprecated
    @JsonIgnore
    public Long promptTokens() {
        return inputTokens;
    }

    @Deprecated
    @JsonIgnore
    public Long completionTokens() {
        return outputTokens;
    }
}
