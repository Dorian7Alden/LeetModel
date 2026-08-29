package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
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
        assertThat(metrics.rawMetrics().getReviewSampleStatistics()).singleElement().satisfies(statistics -> {
            assertThat(statistics.getMean()).isEqualByComparingTo("85.000000");
            assertThat(statistics.getVariance()).isEqualByComparingTo("25.000000");
            assertThat(statistics.getStandardDeviation()).isEqualByComparingTo("5.000000");
            assertThat(statistics.getRange()).isEqualByComparingTo("10.000000");
            assertThat(statistics.getCompleteness()).isEqualTo("COMPLETE");
        });
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
        assertThat(metrics.rawMetrics().getReviewSampleStatistics()).allSatisfy(statistics ->
                assertThat(statistics.getVariance()).isNull());
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
        assertThat(metrics.rawMetrics().getReviewSampleStatistics()).singleElement().satisfies(statistics -> {
            assertThat(statistics.getCompleteness()).isEqualTo("PARTIAL");
            assertThat(statistics.getVariance()).isNull();
            assertThat(statistics.getStandardDeviation()).isNull();
        });
    }

    @Test
    void missingGatewayUsageAndCostRemainExplicitlyMissingInsteadOfZero() {
        EvaluationTask task = task(1, 1);
        AiEvaluationCallAggregateDTO aggregate = new AiEvaluationCallAggregateDTO();
        aggregate.setCallCount(1);
        aggregate.setUsageMissingCount(1);
        aggregate.setCostMissingCount(1);

        var metrics = calculator.calculate(task, List.of(run(11L, 1, "80", 100L)), aggregate);

        assertThat(metrics.rawMetrics().getCallAuditCompleteness()).isEqualTo("COMPLETE");
        assertThat(metrics.rawMetrics().getCallAggregate().getInputTokens()).isNull();
        assertThat(metrics.rawMetrics().getCallAggregate().getCostTotals()).isNull();
        assertThat(metrics.rawMetrics().getCallAggregate().getUsageMissingCount()).isEqualTo(1);
        assertThat(metrics.rawMetrics().getCallAggregate().getCostMissingCount()).isEqualTo(1);
    }

    @Test
    void assistantMetricsKeepEvidenceFreeQualityExplicitlyNotEvaluated() {
        EvaluationTask task = task(2, 1);
        task.setFeatureCode("ASSISTANT");
        task.setWorkflowVersion("ASSISTANT_RAG_V1");
        EvaluationRunAttempt evaluated = run(11L, 1, null, 100L);
        evaluated.setMetricsJson("{\"RETRIEVAL_HIT_RATE\":100,\"SOURCE_COVERAGE_RATE\":50}");
        EvaluationRunAttempt noEvidence = run(12L, 1, null, 100L);
        noEvidence.setMetricsJson("{\"RETRIEVAL_HIT_RATE\":0}");

        var metrics = calculator.calculate(task, List.of(evaluated, noEvidence));
        var summaries = metrics.rawMetrics().getAssistantMetricSummaries();

        assertThat(metrics.validityScore()).isEqualByComparingTo("100.00");
        assertThat(metrics.successRate()).isEqualByComparingTo("100.00");
        assertThat(metrics.stabilityScore()).isNull();

        assertThat(summaries).filteredOn(item -> "RETRIEVAL_HIT_RATE".equals(item.getMetricCode()))
                .singleElement().satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo("AVAILABLE");
                    assertThat(item.getValue()).isEqualByComparingTo("50.00");
                });
        assertThat(summaries).filteredOn(item -> "SOURCE_COVERAGE_RATE".equals(item.getMetricCode()))
                .singleElement().satisfies(item -> assertThat(item.getStatus()).isEqualTo("PARTIAL"));
        assertThat(summaries).filteredOn(item -> "HUMAN_QUALITY_SCORE".equals(item.getMetricCode()))
                .singleElement().satisfies(item -> {
                    assertThat(item.getStatus()).isEqualTo("NOT_EVALUATED");
                    assertThat(item.getValue()).isNull();
                });
    }

    private EvaluationTask task(int totalSlots, int repeatCount) {
        EvaluationTask task = new EvaluationTask();
        task.setTotalSlots(totalSlots);
        task.setRepeatCount(repeatCount);
        task.setFeatureCode("REVIEW");
        return task;
    }

    private EvaluationRunAttempt run(Long sampleId, int repetition, String score, long durationMs) {
        EvaluationRunAttempt run = new EvaluationRunAttempt();
        run.setSampleId(sampleId);
        run.setRepetitionNo(repetition);
        run.setStatus("SUCCEEDED");
        run.setScore(score == null ? null : new BigDecimal(score));
        run.setDurationMs(durationMs);
        return run;
    }
}
