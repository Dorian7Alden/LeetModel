package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageAndCostTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepZeroDistinctFromUnknownAndSerializeAllUsageDimensions() throws Exception {
        AiUsage complete = new AiUsage(100L, 20L, 5L, 0L, 10L, null, 120L,
                AiMetricCompleteness.COMPLETE);
        AiUsage unknown = new AiUsage(null, null, null, null, null, null, null,
                AiMetricCompleteness.UNKNOWN);

        String json = objectMapper.writeValueAsString(complete);

        assertThat(json).contains("\"cacheHitTokens\":0", "\"cacheCreationTokens\":10")
                .contains("\"cacheMissTokens\":null");
        assertThat(validator.validate(complete)).isEmpty();
        assertThat(validator.validate(unknown)).isEmpty();
        assertThat(complete.complete()).isTrue();
        assertThat(unknown.complete()).isFalse();
    }

    @Test
    void shouldPreserveDecimalCostAndRequireSnapshotForEstimate() throws Exception {
        AiCost cost = new AiCost(new BigDecimal("0.00012345"), "CNY",
                AiCostSource.PRICE_SNAPSHOT_ESTIMATED, "PRICE_DEEPSEEK_20260828",
                AiMetricCompleteness.COMPLETE);

        AiCost restored = objectMapper.readValue(objectMapper.writeValueAsBytes(cost), AiCost.class);

        assertThat(restored.amount()).isEqualByComparingTo("0.00012345");
        assertThat(restored.currency()).isEqualTo("CNY");
        assertThat(validator.validate(restored)).isEmpty();
        assertThat(validator.validate(AiCost.unknown())).isEmpty();
    }

    @Test
    void shouldRejectInventedUnknownValuesAndUntraceableEstimate() {
        AiUsage inventedUnknown = new AiUsage(0L, null, null, null, null, null, null,
                AiMetricCompleteness.UNKNOWN);
        AiCost untraceableEstimate = new AiCost(BigDecimal.ONE, "CNY",
                AiCostSource.PRICE_SNAPSHOT_ESTIMATED, null, AiMetricCompleteness.COMPLETE);

        assertThat(validator.validate(inventedUnknown)).isNotEmpty();
        assertThat(validator.validate(untraceableEstimate)).isNotEmpty();
    }
}
