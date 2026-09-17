package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 权重方案中的不可变指标配置。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationWeightItemDTO {
    private String metricCode;
    private String metricVersion;
    private String unit;
    private String normalizationVersion;
    private String normalizationMethod;
    private String clippingPolicy;
    private String missingPolicy;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private BigDecimal targetLowerBound;
    private BigDecimal targetUpperBound;
    private String boundarySource;
    private String boundaryReference;
    private BigDecimal weightPercent;
}
