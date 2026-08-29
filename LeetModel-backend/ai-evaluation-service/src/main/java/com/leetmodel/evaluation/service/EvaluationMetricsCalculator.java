package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.ReviewSampleStatisticsDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * MVP 固定口径：输出有效性、重复分数稳定性、成功率和响应时间。
 */
@Component
public class EvaluationMetricsCalculator {

    private static final long FAST_DURATION_MS = 120_000L;
    private static final long SLOW_DURATION_MS = 300_000L;

    public Metrics calculate(EvaluationTask task, List<EvaluationRunAttempt> latestRuns,
                             AiEvaluationCallAggregateDTO callAggregate) {
        int total = task.getTotalSlots();
        List<EvaluationRunAttempt> succeeded = latestRuns.stream()
                .filter(run -> "SUCCEEDED".equals(run.getStatus()) && run.getScore() != null)
                .toList();
        BigDecimal validity = percent(succeeded.size(), total);
        BigDecimal successRate = percent(succeeded.size(), total);
        Long averageDuration = averageDuration(latestRuns);
        BigDecimal latency = latencyScore(averageDuration);
        BigDecimal stability = task.getRepeatCount() < 2
                ? null : stabilityScore(latestRuns, task.getRepeatCount());
        BigDecimal overall = task.getRepeatCount() < 2
                ? validity.multiply(new BigDecimal("0.85"))
                        .add(latency.multiply(new BigDecimal("0.15")))
                : validity.multiply(new BigDecimal("0.60"))
                        .add(stability.multiply(new BigDecimal("0.30")))
                        .add(latency.multiply(new BigDecimal("0.10")));
        EvaluationRawMetricsDTO raw = rawMetrics(task, latestRuns, callAggregate);
        return new Metrics(validity, stability, successRate, latency,
                overall.setScale(2, RoundingMode.HALF_UP), averageDuration, raw);
    }

    public Metrics calculate(EvaluationTask task, List<EvaluationRunAttempt> latestRuns) {
        return calculate(task, latestRuns, new AiEvaluationCallAggregateDTO());
    }

    private EvaluationRawMetricsDTO rawMetrics(EvaluationTask task,
                                                List<EvaluationRunAttempt> runs,
                                                AiEvaluationCallAggregateDTO aggregate) {
        int succeeded = (int) runs.stream().filter(run -> "SUCCEEDED".equals(run.getStatus())).count();
        Map<String, Integer> failures = new LinkedHashMap<>();
        runs.stream().filter(run -> !"SUCCEEDED".equals(run.getStatus())).forEach(run -> {
            String key = run.getFailureType() == null ? run.getStatus() : run.getFailureType();
            failures.merge(key, 1, Integer::sum);
        });
        List<ReviewSampleStatisticsDTO> review = "REVIEW".equals(task.getFeatureCode())
                ? reviewStatistics(runs, task.getRepeatCount()) : List.of();
        int expectedCalls = task.getTotalSlots() * ("ASSISTANT".equals(task.getFeatureCode())
                && "ASSISTANT_RAG_V1".equals(task.getWorkflowVersion()) ? 2 : 1);
        int observed = aggregate.getCallCount() == null ? 0 : aggregate.getCallCount();
        String auditCompleteness = observed == 0 ? "MISSING"
                : observed < expectedCalls ? "PARTIAL" : "COMPLETE";
        return new EvaluationRawMetricsDTO(EvaluationMetricRegistry.REGISTRY_VERSION,
                task.getTotalSlots(), succeeded, percent(succeeded, task.getTotalSlots()),
                Map.copyOf(failures), percent(succeeded, task.getTotalSlots()), review,
                expectedCalls, observed, auditCompleteness, aggregate);
    }

    private List<ReviewSampleStatisticsDTO> reviewStatistics(List<EvaluationRunAttempt> runs,
                                                              int expectedCount) {
        return runs.stream().collect(Collectors.groupingBy(EvaluationRunAttempt::getSampleId,
                        LinkedHashMap::new, Collectors.toList())).entrySet().stream().map(entry -> {
            List<BigDecimal> scores = entry.getValue().stream()
                    .filter(run -> "SUCCEEDED".equals(run.getStatus()) && run.getScore() != null)
                    .map(EvaluationRunAttempt::getScore).toList();
            BigDecimal mean = scores.isEmpty() ? null : scores.stream().reduce(BigDecimal.ZERO,
                    BigDecimal::add).divide(BigDecimal.valueOf(scores.size()), 6, RoundingMode.HALF_UP);
            BigDecimal variance = null;
            BigDecimal stddev = null;
            BigDecimal range = null;
            if (scores.size() >= 2) {
                variance = scores.stream().map(score -> score.subtract(mean).pow(2))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scores.size()), 6, RoundingMode.HALF_UP);
                stddev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                        .setScale(6, RoundingMode.HALF_UP);
                BigDecimal min = scores.stream().min(BigDecimal::compareTo).orElseThrow();
                BigDecimal max = scores.stream().max(BigDecimal::compareTo).orElseThrow();
                range = max.subtract(min).setScale(6, RoundingMode.HALF_UP);
            }
            String completeness = scores.isEmpty() ? "MISSING"
                    : scores.size() < expectedCount ? "PARTIAL"
                    : scores.size() < 2 ? "INSUFFICIENT" : "COMPLETE";
            return new ReviewSampleStatisticsDTO(entry.getKey(), scores.size(), expectedCount,
                    completeness, mean, variance, stddev, range);
        }).toList();
    }

    private BigDecimal stabilityScore(List<EvaluationRunAttempt> latestRuns, int repeatCount) {
        Map<Long, List<EvaluationRunAttempt>> bySample = latestRuns.stream()
                .collect(Collectors.groupingBy(EvaluationRunAttempt::getSampleId));
        if (bySample.isEmpty()) return BigDecimal.ZERO.setScale(2);
        double averageRange = bySample.values().stream().mapToDouble(runs -> {
            List<EvaluationRunAttempt> succeeded = runs.stream()
                    .filter(run -> "SUCCEEDED".equals(run.getStatus()) && run.getScore() != null).toList();
            if (succeeded.size() < repeatCount) return 100D;
            DoubleSummaryStatistics stats = succeeded.stream()
                    .mapToDouble(run -> run.getScore().doubleValue()).summaryStatistics();
            return stats.getMax() - stats.getMin();
        }).average().orElse(100D);
        return decimal(Math.max(0D, 100D - averageRange));
    }

    private Long averageDuration(List<EvaluationRunAttempt> runs) {
        return Math.round(runs.stream().filter(run -> run.getDurationMs() != null)
                .mapToLong(EvaluationRunAttempt::getDurationMs).average().orElse(0D));
    }

    private BigDecimal latencyScore(Long durationMs) {
        if (durationMs <= FAST_DURATION_MS) return new BigDecimal("100.00");
        if (durationMs >= SLOW_DURATION_MS) return new BigDecimal("0.00");
        double score = 100D * (SLOW_DURATION_MS - durationMs)
                / (SLOW_DURATION_MS - FAST_DURATION_MS);
        return decimal(score);
    }

    private BigDecimal percent(int numerator, int denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public record Metrics(BigDecimal validityScore, BigDecimal stabilityScore,
                          BigDecimal successRate, BigDecimal latencyScore,
                          BigDecimal overallScore, Long averageDurationMs,
                          EvaluationRawMetricsDTO rawMetrics) {
    }
}
