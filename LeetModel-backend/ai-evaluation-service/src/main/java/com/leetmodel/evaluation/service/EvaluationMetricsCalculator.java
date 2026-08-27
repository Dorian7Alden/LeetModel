package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MVP 固定口径：输出有效性、重复分数稳定性、成功率和响应时间。
 */
@Component
public class EvaluationMetricsCalculator {

    private static final long FAST_DURATION_MS = 120_000L;
    private static final long SLOW_DURATION_MS = 300_000L;

    public Metrics calculate(EvaluationTask task, List<EvaluationRunAttempt> latestRuns) {
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
        return new Metrics(validity, stability, successRate, latency,
                overall.setScale(2, RoundingMode.HALF_UP), averageDuration);
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
                          BigDecimal overallScore, Long averageDurationMs) {
    }
}
