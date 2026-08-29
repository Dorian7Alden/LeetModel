package com.leetmodel.evaluation.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.EvaluationWeightItemDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EvaluationScoreResultServiceTest {

    private final JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final EvaluationScoreResultService service = new EvaluationScoreResultService(
            new EvaluationRawMetricExtractor(), new EvaluationNormalizationService(),
            mock(EvaluationScoreResultMapper.class), mock(EvaluationScoreResultItemMapper.class), objectMapper);

    @Test
    void calculatesIndexAndPreservesRawNormalizedWeightAndContribution() throws Exception {
        EvaluationTask task = task(scheme(
                item("RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                        "HIGHER_IS_BETTER", "0", "100", "60.0000"),
                item("TOTAL_DURATION_MS", "TOTAL_DURATION_MS_V1", "MILLISECOND",
                        "LOWER_IS_BETTER", "0", "10000", "40.0000")));
        EvaluationRawMetricsDTO raw = rawMetrics("80", 2500L, 0);
        String rawJson = objectMapper.writeValueAsString(raw);

        var bundle = service.calculateInitial(task, raw, rawJson);

        assertThat(bundle.result().getScoreResultVersion()).isEqualTo("SCORE_RESULT_V1");
        assertThat(bundle.result().getStatus()).isEqualTo("CALCULATED");
        assertThat(bundle.result().getVersionSelectionIndex()).isEqualByComparingTo("78.000000");
        assertThat(bundle.result().getRawMetricsSnapshotJson()).isEqualTo(rawJson);
        assertThat(bundle.items()).satisfiesExactly(
                success -> {
                    assertThat(success.getRawValue()).isEqualByComparingTo("80");
                    assertThat(success.getNormalizedValue()).isEqualByComparingTo("80");
                    assertThat(success.getWeightPercent()).isEqualByComparingTo("60");
                    assertThat(success.getContributionValue()).isEqualByComparingTo("48");
                },
                duration -> {
                    assertThat(duration.getRawValue()).isEqualByComparingTo("2500");
                    assertThat(duration.getNormalizedValue()).isEqualByComparingTo("75");
                    assertThat(duration.getWeightPercent()).isEqualByComparingTo("40");
                    assertThat(duration.getContributionValue()).isEqualByComparingTo("30");
                });
    }

    @Test
    void missingRequiredMetricMakesIndexUnavailableWithoutZeroFill() throws Exception {
        EvaluationTask task = task(scheme(
                item("RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                        "HIGHER_IS_BETTER", "0", "100", "60.0000"),
                item("TOTAL_DURATION_MS", "TOTAL_DURATION_MS_V1", "MILLISECOND",
                        "LOWER_IS_BETTER", "0", "10000", "40.0000")));
        EvaluationRawMetricsDTO raw = rawMetrics("80", 2500L, 1);

        var bundle = service.calculateInitial(task, raw, objectMapper.writeValueAsString(raw));

        assertThat(bundle.result().getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(bundle.result().getVersionSelectionIndex()).isNull();
        assertThat(bundle.result().getUnavailableReason()).contains("TOTAL_DURATION_MS");
        assertThat(bundle.items().get(1).getRawAvailability()).isEqualTo("UNAVAILABLE");
        assertThat(bundle.items().get(1).getRawValue()).isNull();
        assertThat(bundle.items().get(1).getNormalizedValue()).isNull();
        assertThat(bundle.items().get(1).getContributionValue()).isNull();
        assertThat(bundle.items().get(0).getContributionValue()).isEqualByComparingTo("48");
    }

    @Test
    void estimatedOrMultiCurrencyCostCannotBecomeComparableActualCost() {
        AiEvaluationCallAggregateDTO aggregate = new AiEvaluationCallAggregateDTO();
        aggregate.setCostTotals(Map.of("CNY", BigDecimal.ONE, "USD", BigDecimal.ONE));
        aggregate.setEstimatedCostCount(1);
        EvaluationRawMetricsDTO raw = new EvaluationRawMetricsDTO();
        raw.setCallAggregate(aggregate);

        var value = new EvaluationRawMetricExtractor().extract("ACTUAL_COST", raw);

        assertThat(value.availability()).isEqualTo("UNAVAILABLE");
        assertThat(value.value()).isNull();
    }

    @Test
    void recalculationKeepsRawSnapshotAndRecordsNewSchemeAndOperator() throws Exception {
        EvaluationTask task = task(scheme(item(
                "RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                "HIGHER_IS_BETTER", "0", "100", "100.0000")));
        EvaluationWeightSchemeDTO newScheme = new EvaluationWeightSchemeDTO(
                702L, "REVIEW_SUCCESS", "REVIEW_SUCCESS_V2", "成功优先", "重算目标",
                "REVIEW", "METRIC_SET_V2", "ACTIVE", 9L, null, null, null,
                List.of(item("RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                        "HIGHER_IS_BETTER", "0", "100", "100.0000")));
        EvaluationRawMetricsDTO raw = rawMetrics("80", 2500L, 0);
        String rawJson = objectMapper.writeValueAsString(raw);

        var bundle = service.calculateRecalculation(task, raw, rawJson, newScheme, 9L);

        assertThat(bundle.result().getScoreResultVersion()).isNull();
        assertThat(bundle.result().getWeightSchemeId()).isEqualTo(702L);
        assertThat(bundle.result().getWeightSchemeVersion()).isEqualTo("REVIEW_SUCCESS_V2");
        assertThat(bundle.result().getCalculatedBy()).isEqualTo(9L);
        assertThat(bundle.result().getRawMetricsSnapshotJson()).isEqualTo(rawJson);
        assertThat(bundle.result().getVersionSelectionIndex()).isEqualByComparingTo("80");
    }

    private EvaluationTask task(EvaluationWeightSchemeDTO scheme) throws Exception {
        EvaluationTask task = new EvaluationTask();
        task.setId(20L);
        task.setFeatureCode("REVIEW");
        task.setMetricSetVersion("METRIC_SET_V2");
        task.setWeightSchemeId(701L);
        task.setWeightSchemeVersion("REVIEW_BALANCED_V1");
        task.setWeightSchemeSnapshotJson(objectMapper.writeValueAsString(scheme));
        return task;
    }

    private EvaluationWeightSchemeDTO scheme(EvaluationWeightItemDTO... items) {
        return new EvaluationWeightSchemeDTO(
                701L, "REVIEW_BALANCED", "REVIEW_BALANCED_V1", "均衡方案", "成功率与耗时",
                "REVIEW", "METRIC_SET_V2", "ACTIVE", 9L, null, null, null, List.of(items));
    }

    private EvaluationWeightItemDTO item(String code, String version, String unit,
                                         String method, String lower, String upper, String weight) {
        return new EvaluationWeightItemDTO(
                code, version, unit, code + "_NORMALIZATION_V1", method,
                "CLAMP_0_100", "MARK_UNAVAILABLE", new BigDecimal(lower), new BigDecimal(upper),
                null, null, "BUSINESS_THRESHOLD", code + "_SLO_V1", new BigDecimal(weight));
    }

    private EvaluationRawMetricsDTO rawMetrics(String successRate, Long totalDuration,
                                                int durationMissing) {
        AiEvaluationCallAggregateDTO aggregate = new AiEvaluationCallAggregateDTO();
        aggregate.setCallCount(1);
        aggregate.setAverageTotalMs(totalDuration);
        aggregate.setDurationMissingCount(durationMissing);
        EvaluationRawMetricsDTO raw = new EvaluationRawMetricsDTO();
        raw.setMetricSetVersion("METRIC_SET_V2");
        raw.setRunSuccessRate(new BigDecimal(successRate));
        raw.setCallAggregate(aggregate);
        return raw;
    }
}
