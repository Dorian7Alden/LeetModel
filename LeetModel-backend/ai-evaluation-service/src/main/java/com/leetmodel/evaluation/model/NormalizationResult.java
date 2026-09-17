package com.leetmodel.evaluation.model;

import java.math.BigDecimal;

/** 单个原始指标的归一化结果；不可用状态不携带伪造的零值。 */
public record NormalizationResult(
        NormalizationAvailability availability,
        BigDecimal normalizedValue
) {

    public NormalizationResult {
        if (availability == null) throw new IllegalArgumentException("归一化可用性不能为空");
        if (availability == NormalizationAvailability.AVAILABLE && normalizedValue == null) {
            throw new IllegalArgumentException("可用归一化结果必须包含数值");
        }
        if (availability != NormalizationAvailability.AVAILABLE && normalizedValue != null) {
            throw new IllegalArgumentException("不可用归一化结果不能包含数值");
        }
    }
}
