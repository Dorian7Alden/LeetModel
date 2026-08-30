package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_score_result_item")
public class EvaluationScoreResultItem extends BaseEntity {
    private Long scoreResultId;
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
