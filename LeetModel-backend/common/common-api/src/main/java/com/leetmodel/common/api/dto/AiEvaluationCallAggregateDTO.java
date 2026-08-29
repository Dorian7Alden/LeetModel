package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/** AI 网关按评价任务聚合的调用资源事实，不包含请求或响应正文。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiEvaluationCallAggregateDTO {
    private Integer callCount;
    private Integer succeededCallCount;
    private Integer failedCallCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheHitTokens;
    private Long cacheCreationTokens;
    private Long cacheMissTokens;
    private Long totalTokens;
    private Integer usageCompleteCount;
    private Integer usageMissingCount;
    private Map<String, BigDecimal> costTotals;
    private Integer actualCostCount;
    private Integer estimatedCostCount;
    private Integer costMissingCount;
    private Long averageQueueMs;
    private Long averageExecutionMs;
    private Long averageTotalMs;
    private Integer durationMissingCount;
}
