package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_weight_scheme")
public class EvaluationWeightScheme extends BaseEntity {
    private String schemeCode;
    private String schemeVersion;
    private String name;
    private String objective;
    private String featureCode;
    private String metricSetVersion;
    private String status;
    private Long createdBy;
    private Long deactivatedBy;
    private LocalDateTime deactivatedAt;
}
