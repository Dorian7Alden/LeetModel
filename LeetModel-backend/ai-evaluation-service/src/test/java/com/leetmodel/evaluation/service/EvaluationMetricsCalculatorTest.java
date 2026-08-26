package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMetricsCalculatorTest {

    private final EvaluationMetricsCalculator calculator = new EvaluationMetricsCalculator();

    @Test
    void repeatedRunsMeasureValidityScoreRangeAndFixedLatencyBaseline() {
        EvaluationTask task = task(2, 2);
        List<EvaluationRunAttempt> runs = List.of(
                run(11L, 1, "80", 100_000L),
                run(11L, 2, "90", 120_000L));

        var metrics = calculator.calculate(task, runs);

        assertThat(metrics.validityScore()).isEqualByComparingTo("100.00");
        assertThat(metrics.successRate()).isEqualByComparingTo("100.00");
        assertThat(metrics.stabilityScore()).isEqualByComparingTo("90.00");
        assertThat(metrics.latencyScore()).isEqualByComparingTo("100.00");
        assertThat(metrics.overallScore()).isEqualByComparingTo("97.00");
        assertThat(metrics.averageDurationMs()).isEqualTo(110_000L);
    }

    @Test
    void singleRunOmitsStabilityAndDoesNotRewardInvalidOutput() {
        EvaluationTask task = task(2, 1);
        EvaluationRunAttempt success = run(11L, 1, "75", 300_000L);
        EvaluationRunAttempt invalid = new EvaluationRunAttempt();
        invalid.setSampleId(12L);
        invalid.setStatus("FAILED");
        invalid.setFailureType("OUTPUT");
        invalid.setDurationMs(300_000L);

        var metrics = calculator.calculate(task, List.of(success, invalid));

        assertThat(metrics.validityScore()).isEqualByComparingTo("50.00");
        assertThat(metrics.stabilityScore()).isNull();
        assertThat(metrics.latencyScore()).isEqualByComparingTo("0.00");
        assertThat(metrics.overallScore()).isEqualByComparingTo("42.50");
    }

    @Test
    void missingRepeatedOutputCannotAppearPerfectlyStable() {
        EvaluationTask task = task(2, 2);
        EvaluationRunAttempt success = run(11L, 1, "75", 100_000L);
        EvaluationRunAttempt invalid = new EvaluationRunAttempt();
        invalid.setSampleId(11L);
        invalid.setRepetitionNo(2);
        invalid.setStatus("FAILED");
        invalid.setFailureType("OUTPUT");
        invalid.setDurationMs(100_000L);

        var metrics = calculator.calculate(task, List.of(success, invalid));

        assertThat(metrics.validityScore()).isEqualByComparingTo("50.00");
        assertThat(metrics.stabilityScore()).isEqualByComparingTo("0.00");
        assertThat(metrics.overallScore()).isEqualByComparingTo("40.00");
    }

    private EvaluationTask task(int totalSlots, int repeatCount) {
        EvaluationTask task = new EvaluationTask();
        task.setTotalSlots(totalSlots);
        task.setRepeatCount(repeatCount);
        return task;
    }

    private EvaluationRunAttempt run(Long sampleId, int repetition, String score, long durationMs) {
        EvaluationRunAttempt run = new EvaluationRunAttempt();
        run.setSampleId(sampleId);
        run.setRepetitionNo(repetition);
        run.setStatus("SUCCEEDED");
        run.setScore(new BigDecimal(score));
        run.setDurationMs(durationMs);
        return run;
    }
}
