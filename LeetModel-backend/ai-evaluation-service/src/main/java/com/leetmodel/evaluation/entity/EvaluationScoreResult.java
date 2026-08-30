package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_score_result")
public class EvaluationScoreResult extends BaseEntity {
    private Long taskId;
    private String scoreResultVersion;
    private Long weightSchemeId;
    private String weightSchemeVersion;
    private String metricSetVersion;
    private String weightSchemeSnapshotJson;
    private String rawMetricsSnapshotJson;
    private String status;
    private BigDecimal versionSelectionIndex;
    private String unavailableReason;
    private Long calculatedBy;
}
