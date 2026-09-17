package com.leetmodel.common.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 可按调用元数据过滤的 AI 调用聚合，不跨币种强行合计费用。 */
@Data
@NoArgsConstructor
public class AiCallStatsDTO {
    private Long totalCount;
    private Long successCount;
    private Long failureCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheHitTokens;
    private Long cacheCreationTokens;
    private Long totalTokens;
    private Long averageQueueMs;
    private Long averageExecutionMs;
    private Long averageTotalMs;
    private BigDecimal knownCostAmount;
    private String costCurrency;
    private Long actualCostCount;
    private Long estimatedCostCount;
    private Long unknownCostCount;

    public AiCallStatsDTO(Long totalCount, Long successCount, Long failureCount,
                          Long totalTokens, Long averageDurationMs) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalTokens = totalTokens;
        this.averageTotalMs = averageDurationMs;
    }

    @Deprecated
    public Long getAverageDurationMs() {
        return averageTotalMs;
    }
}
