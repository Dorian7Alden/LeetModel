package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 单次调用的实际或估算费用快照。金额单位由 currency 指定。 */
public record AiCost(
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        AiCostSource source,
        @Size(max = 100) String priceSnapshotVersion,
        AiMetricCompleteness completeness
) {
    @JsonIgnore
    @AssertTrue(message = "费用值、来源与完整性不一致")
    public boolean isConsistent() {
        if (source == null || completeness == null) return false;
        if (source == AiCostSource.UNKNOWN || completeness == AiMetricCompleteness.UNKNOWN) {
            return source == AiCostSource.UNKNOWN && completeness == AiMetricCompleteness.UNKNOWN
                    && amount == null && currency == null && priceSnapshotVersion == null;
        }
        if (amount == null || currency == null) return false;
        return source != AiCostSource.PRICE_SNAPSHOT_ESTIMATED || priceSnapshotVersion != null;
    }

    public static AiCost unknown() {
        return new AiCost(null, null, AiCostSource.UNKNOWN, null, AiMetricCompleteness.UNKNOWN);
    }
}
