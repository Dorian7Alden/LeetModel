package com.leetmodel.evaluation.model;

import java.math.BigDecimal;

/** 从可信原始指标快照中提取的单项数值和证据可用性。 */
public record EvaluationRawMetricValue(String availability, BigDecimal value) {

    public EvaluationRawMetricValue {
        if (availability == null || availability.isBlank()) {
            throw new IllegalArgumentException("原始指标可用性不能为空");
        }
        if ("AVAILABLE".equals(availability) && value == null) {
            throw new IllegalArgumentException("可用原始指标必须包含数值");
        }
        if (!"AVAILABLE".equals(availability) && value != null) {
            throw new IllegalArgumentException("不可用原始指标不能包含数值");
        }
    }
}
