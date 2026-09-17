package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 版本选择指数的一项可复算贡献。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationScoreItemDTO {
    private String metricCode;
    private String metricVersion;
    private String rawAvailability;
    private BigDecimal rawValue;
    private String normalizationVersion;
    private String normalizationAvailability;
    private BigDecimal normalizedValue;
    private BigDecimal weightPercent;
    private BigDecimal contributionValue;
}
