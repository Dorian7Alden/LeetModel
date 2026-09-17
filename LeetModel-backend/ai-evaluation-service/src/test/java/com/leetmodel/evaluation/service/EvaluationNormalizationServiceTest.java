package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.model.NormalizationAvailability;
import com.leetmodel.evaluation.model.NormalizationBoundarySource;
import com.leetmodel.evaluation.model.NormalizationClippingPolicy;
import com.leetmodel.evaluation.model.NormalizationConfiguration;
import com.leetmodel.evaluation.model.NormalizationMethod;
import com.leetmodel.evaluation.model.NormalizationMissingPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationNormalizationServiceTest {

    private final EvaluationNormalizationService service = new EvaluationNormalizationService();

    @Test
    void higherIsBetterUsesFixedBoundsAndClipsOutliers() {
        NormalizationConfiguration configuration = linear(NormalizationMethod.HIGHER_IS_BETTER);

        assertValue(configuration, "-1", "0");
        assertValue(configuration, "25", "25");
        assertValue(configuration, "100", "100");
        assertValue(configuration, "150", "100");
    }

    @Test
    void lowerIsBetterReversesFixedBounds() {
        NormalizationConfiguration configuration = linear(NormalizationMethod.LOWER_IS_BETTER);

        assertValue(configuration, "-1", "100");
        assertValue(configuration, "25", "75");
        assertValue(configuration, "100", "0");
        assertValue(configuration, "150", "0");
    }

    @Test
    void targetRangeAwardsTargetAndDecaysOnBothSides() {
        NormalizationConfiguration configuration = new NormalizationConfiguration(
                "QUEUE_TARGET_V1", "QUEUE_DURATION_MS", "QUEUE_DURATION_MS_V1", "MILLISECOND",
                NormalizationMethod.TARGET_RANGE, NormalizationClippingPolicy.CLAMP_0_100,
                NormalizationMissingPolicy.MARK_UNAVAILABLE,
                value("0"), value("100"), value("40"), value("60"),
                NormalizationBoundarySource.BUSINESS_THRESHOLD, "SLO_QUEUE_2026_08", Set.of("REVIEW"));

        assertValue(configuration, "0", "0");
        assertValue(configuration, "20", "50");
        assertValue(configuration, "40", "100");
        assertValue(configuration, "50", "100");
        assertValue(configuration, "60", "100");
        assertValue(configuration, "80", "50");
        assertValue(configuration, "100", "0");
    }

    @Test
    void missingAndNotNormalizableNeverBecomeZero() {
        NormalizationConfiguration linear = linear(NormalizationMethod.HIGHER_IS_BETTER);
        NormalizationConfiguration unavailable = new NormalizationConfiguration(
                "REVIEW_SCORE_V1", "REVIEW_SCORE", "REVIEW_SCORE_V1", "SCORE",
                NormalizationMethod.NOT_NORMALIZABLE, NormalizationClippingPolicy.CLAMP_0_100,
                NormalizationMissingPolicy.MARK_NOT_EVALUATED, null, null, null, null,
                NormalizationBoundarySource.NOT_APPLICABLE, null, Set.of("REVIEW"));

        assertThat(service.normalize(null, linear))
                .extracting("availability", "normalizedValue")
                .containsExactly(NormalizationAvailability.UNAVAILABLE, null);
        assertThat(service.normalize(value("80"), unavailable))
                .extracting("availability", "normalizedValue")
                .containsExactly(NormalizationAvailability.NOT_NORMALIZABLE, null);
    }

    @Test
    void sameRawValueIsStableForConstantBatchBecauseBatchExtremaAreNotInputs() {
        NormalizationConfiguration configuration = linear(NormalizationMethod.HIGHER_IS_BETTER);

        assertThat(service.normalize(value("42"), configuration))
                .isEqualTo(service.normalize(value("42"), configuration));
        assertValue(configuration, "42", "42");
    }

    @Test
    void invalidOrUntraceableBoundsAreRejected() {
        assertThatThrownBy(() -> new NormalizationConfiguration(
                "BAD_V1", "RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                NormalizationMethod.HIGHER_IS_BETTER, NormalizationClippingPolicy.CLAMP_0_100,
                NormalizationMissingPolicy.MARK_UNAVAILABLE, value("10"), value("10"), null, null,
                NormalizationBoundarySource.BUSINESS_THRESHOLD, "SLO", Set.of("REVIEW")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("下界必须小于上界");

        assertThatThrownBy(() -> new NormalizationConfiguration(
                "BAD_V2", "RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                NormalizationMethod.HIGHER_IS_BETTER, NormalizationClippingPolicy.CLAMP_0_100,
                NormalizationMissingPolicy.MARK_UNAVAILABLE, value("0"), value("100"), null, null,
                NormalizationBoundarySource.VERSIONED_BASELINE, " ", Set.of("REVIEW")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("边界引用不能为空");
    }

    private NormalizationConfiguration linear(NormalizationMethod method) {
        return new NormalizationConfiguration(
                "RUN_SUCCESS_RATE_NORMALIZATION_V1", "RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                method, NormalizationClippingPolicy.CLAMP_0_100,
                NormalizationMissingPolicy.MARK_UNAVAILABLE, value("0"), value("100"), null, null,
                NormalizationBoundarySource.BUSINESS_THRESHOLD, "RUN_SUCCESS_RATE_SLO_V1",
                Set.of("REVIEW", "ASSISTANT"));
    }

    private void assertValue(NormalizationConfiguration configuration, String raw, String expected) {
        var result = service.normalize(value(raw), configuration);

        assertThat(result.availability()).isEqualTo(NormalizationAvailability.AVAILABLE);
        assertThat(result.normalizedValue()).isEqualByComparingTo(expected);
    }

    private BigDecimal value(String number) {
        return new BigDecimal(number);
    }
}
