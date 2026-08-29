package com.leetmodel.evaluation.model;

import java.util.Set;

/** 一个不可变、可审计的评价指标口径定义。 */
public record EvaluationMetricDefinition(
        String metricCode,
        String metricVersion,
        String category,
        String unit,
        String direction,
        String source,
        Set<String> applicableFeatures,
        String missingPolicy,
        String evidenceRequirement
) {
}
