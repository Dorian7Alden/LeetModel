package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_weight_scheme_item")
public class EvaluationWeightSchemeItem extends BaseEntity {
    private Long schemeId;
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
