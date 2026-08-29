package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 一次评价任务的可信原始指标与完整性，不包含归一化或权重。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRawMetricsDTO {
    private String metricSetVersion;
    private Integer totalSlots;
    private Integer succeededSlots;
    private BigDecimal runSuccessRate;
    private Map<String, Integer> failureCounts;
    private BigDecimal structureValidRate;
    private List<ReviewSampleStatisticsDTO> reviewSampleStatistics;
    private List<AssistantMetricSummaryDTO> assistantMetricSummaries;
    private Integer expectedCallCount;
    private Integer observedCallCount;
    private String callAuditCompleteness;
    private AiEvaluationCallAggregateDTO callAggregate;
}
