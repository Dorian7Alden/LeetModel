package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import com.leetmodel.common.api.dto.AssistantMetricSummaryDTO;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.ReviewSampleStatisticsDTO;
import com.leetmodel.evaluation.model.EvaluationRawMetricValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 从任务可信原始指标快照中按注册指标编码提取任务级数值。 */
@Component
public class EvaluationRawMetricExtractor {

    /**
     * 提取一个待归一化指标，缺失或证据不完整时保留状态而不填零。
     * @param metricCode 注册指标编码
     * @param rawMetrics 可信原始指标快照
     * @return 原始值与可用性
     */
    public EvaluationRawMetricValue extract(String metricCode, EvaluationRawMetricsDTO rawMetrics) {
        return switch (metricCode) {
            case "RUN_SUCCESS_RATE" -> available(rawMetrics.getRunSuccessRate());
            case "STRUCTURE_VALID_RATE" -> available(rawMetrics.getStructureValidRate());
            case "INPUT_TOKENS" -> token(rawMetrics, true);
            case "OUTPUT_TOKENS" -> token(rawMetrics, false);
            case "ACTUAL_COST" -> actualCost(rawMetrics);
            case "QUEUE_DURATION_MS" -> duration(rawMetrics, "QUEUE");
            case "EXECUTION_DURATION_MS" -> duration(rawMetrics, "EXECUTION");
            case "TOTAL_DURATION_MS" -> duration(rawMetrics, "TOTAL");
            case "REVIEW_SCORE_MEAN" -> reviewAverage(rawMetrics, "MEAN");
            case "REVIEW_SCORE_VARIANCE" -> reviewAverage(rawMetrics, "VARIANCE");
            case "REVIEW_SCORE_STDDEV" -> reviewAverage(rawMetrics, "STDDEV");
            case "REVIEW_SCORE_RANGE" -> reviewAverage(rawMetrics, "RANGE");
            case "RETRIEVAL_HIT_RATE", "SOURCE_COVERAGE_RATE", "FORMAT_RULE_PASS_RATE",
                    "EXPECTED_POINT_COVERAGE_RATE", "HUMAN_QUALITY_SCORE" ->
                    assistantMetric(rawMetrics, metricCode);
            default -> unavailable("UNAVAILABLE");
        };
    }

    /**
     * 提取完整 usage 下的 Token 值。
     * @param rawMetrics 原始指标
     * @param input 是否输入 Token
     * @return Token 指标
     */
    private EvaluationRawMetricValue token(EvaluationRawMetricsDTO rawMetrics, boolean input) {
        AiEvaluationCallAggregateDTO aggregate = rawMetrics.getCallAggregate();
        if (aggregate == null || !completeCount(aggregate.getCallCount(),
                aggregate.getUsageCompleteCount(), aggregate.getUsageMissingCount())) {
            return unavailable("UNAVAILABLE");
        }
        Long value = input ? aggregate.getInputTokens() : aggregate.getOutputTokens();
        return value == null ? unavailable("UNAVAILABLE") : available(BigDecimal.valueOf(value));
    }

    /**
     * 只在单币种、全部为供应商实际费用且无缺失时提取费用。
     * @param rawMetrics 原始指标
     * @return 实际费用指标
     */
    private EvaluationRawMetricValue actualCost(EvaluationRawMetricsDTO rawMetrics) {
        AiEvaluationCallAggregateDTO aggregate = rawMetrics.getCallAggregate();
        if (aggregate == null || !completeCount(aggregate.getCallCount(),
                aggregate.getActualCostCount(), aggregate.getCostMissingCount())
                || aggregate.getEstimatedCostCount() == null || aggregate.getEstimatedCostCount() != 0
                || aggregate.getCostTotals() == null || aggregate.getCostTotals().size() != 1) {
            return unavailable("UNAVAILABLE");
        }
        return available(aggregate.getCostTotals().values().iterator().next());
    }

    /**
     * 提取所有调用都有耗时事实时的平均耗时。
     * @param rawMetrics 原始指标
     * @param kind 耗时种类
     * @return 耗时指标
     */
    private EvaluationRawMetricValue duration(EvaluationRawMetricsDTO rawMetrics, String kind) {
        AiEvaluationCallAggregateDTO aggregate = rawMetrics.getCallAggregate();
        if (aggregate == null || aggregate.getCallCount() == null || aggregate.getCallCount() <= 0
                || aggregate.getDurationMissingCount() == null || aggregate.getDurationMissingCount() != 0) {
            return unavailable("UNAVAILABLE");
        }
        Long value = switch (kind) {
            case "QUEUE" -> aggregate.getAverageQueueMs();
            case "EXECUTION" -> aggregate.getAverageExecutionMs();
            default -> aggregate.getAverageTotalMs();
        };
        return value == null ? unavailable("UNAVAILABLE") : available(BigDecimal.valueOf(value));
    }

    /**
     * 对完整 REVIEW 样本统计量取任务级算术平均。
     * @param rawMetrics 原始指标
     * @param kind 统计量种类
     * @return REVIEW 统计指标
     */
    private EvaluationRawMetricValue reviewAverage(EvaluationRawMetricsDTO rawMetrics, String kind) {
        List<ReviewSampleStatisticsDTO> statistics = rawMetrics.getReviewSampleStatistics();
        if (statistics == null || statistics.isEmpty()) return unavailable("UNAVAILABLE");
        List<BigDecimal> values = statistics.stream()
                .filter(item -> completeFor(kind, item))
                .map(item -> reviewValue(kind, item))
                .toList();
        if (values.size() != statistics.size()) return unavailable("UNAVAILABLE");
        return available(average(values));
    }

    /**
     * 判断样本统计量对目标字段是否完整。
     * @param kind 统计量种类
     * @param statistics 样本统计量
     * @return 是否完整
     */
    private boolean completeFor(String kind, ReviewSampleStatisticsDTO statistics) {
        if ("MEAN".equals(kind)) {
            return statistics.getMean() != null
                    && !List.of("MISSING", "PARTIAL").contains(statistics.getCompleteness());
        }
        return "COMPLETE".equals(statistics.getCompleteness()) && reviewValue(kind, statistics) != null;
    }

    /**
     * 读取 REVIEW 统计字段。
     * @param kind 统计量种类
     * @param statistics 样本统计量
     * @return 数值
     */
    private BigDecimal reviewValue(String kind, ReviewSampleStatisticsDTO statistics) {
        return switch (kind) {
            case "MEAN" -> statistics.getMean();
            case "VARIANCE" -> statistics.getVariance();
            case "STDDEV" -> statistics.getStandardDeviation();
            default -> statistics.getRange();
        };
    }

    /**
     * 提取具有完整证据覆盖的客服指标。
     * @param rawMetrics 原始指标
     * @param metricCode 指标编码
     * @return 客服指标
     */
    private EvaluationRawMetricValue assistantMetric(EvaluationRawMetricsDTO rawMetrics, String metricCode) {
        if (rawMetrics.getAssistantMetricSummaries() == null) return unavailable("UNAVAILABLE");
        AssistantMetricSummaryDTO summary = rawMetrics.getAssistantMetricSummaries().stream()
                .filter(item -> metricCode.equals(item.getMetricCode()))
                .findFirst().orElse(null);
        if (summary == null) return unavailable("UNAVAILABLE");
        if (!"AVAILABLE".equals(summary.getStatus()) || summary.getValue() == null) {
            return unavailable(summary.getStatus() == null ? "UNAVAILABLE" : summary.getStatus());
        }
        return available(summary.getValue());
    }

    /**
     * 计算非空数值平均值。
     * @param values 数值列表
     * @return 六位小数平均值
     */
    private BigDecimal average(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    /**
     * 判断全部调用是否都有对应完整事实。
     * @param total 调用总数
     * @param complete 完整数
     * @param missing 缺失数
     * @return 是否完整
     */
    private boolean completeCount(Integer total, Integer complete, Integer missing) {
        return total != null && total > 0 && complete != null && complete.equals(total)
                && missing != null && missing == 0;
    }

    /**
     * 创建可用原始指标。
     * @param value 原始值
     * @return 可用结果或显式不可用
     */
    private EvaluationRawMetricValue available(BigDecimal value) {
        return value == null ? unavailable("UNAVAILABLE") : new EvaluationRawMetricValue("AVAILABLE", value);
    }

    /**
     * 创建不可用原始指标。
     * @param availability 不可用状态
     * @return 不携带数值的结果
     */
    private EvaluationRawMetricValue unavailable(String availability) {
        return new EvaluationRawMetricValue(availability, null);
    }
}
