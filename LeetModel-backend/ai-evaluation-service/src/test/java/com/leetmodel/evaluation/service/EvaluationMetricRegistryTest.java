package com.leetmodel.evaluation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationMetricRegistryTest {

    private final EvaluationMetricRegistry registry = new EvaluationMetricRegistry();

    @Test
    void resourceFactsCannotMasqueradeAsQuality() {
        assertThat(registry.require("INPUT_TOKENS").category()).isEqualTo("RESOURCE");
        assertThat(registry.require("TOTAL_DURATION_MS").category()).isEqualTo("RUNNING");
        assertThat(registry.require("HUMAN_QUALITY_SCORE").category()).isEqualTo("HUMAN_QUALITY");
    }

    @Test
    void metricDeclaresDirectionSourceVersionAndMissingPolicy() {
        var definition = registry.require("REVIEW_SCORE_STDDEV");

        assertThat(definition.metricVersion()).isEqualTo("REVIEW_SCORE_STDDEV_V1");
        assertThat(definition.direction()).isEqualTo("LOWER_IS_BETTER");
        assertThat(definition.source()).isEqualTo("EVALUATION_CALCULATION");
        assertThat(definition.missingPolicy()).isEqualTo("MARK_UNAVAILABLE");
    }

    @Test
    void featureApplicabilityIsEnforced() {
        assertThatThrownBy(() -> registry.requireApplicable("REVIEW_SCORE_MEAN", "ASSISTANT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不适用于");
    }

    @Test
    void unknownMetricCannotParticipateInCalculation() {
        assertThatThrownBy(() -> registry.require("ACCURACY_FROM_LATENCY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知评价指标");
    }
}
