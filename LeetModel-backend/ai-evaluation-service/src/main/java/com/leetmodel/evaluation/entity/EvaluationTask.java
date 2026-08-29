package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_task")
public class EvaluationTask extends BaseEntity {
    private Long datasetId;
    private String datasetVersion;
    private String featureCode;
    private String workflowVersion;
    private String modelExecutionConfigVersion;
    private String ragIndexVersion;
    private String metricSetVersion;
    private Long weightSchemeId;
    private String weightSchemeVersion;
    private String weightSchemeSnapshotJson;
    private String workflowSnapshotJson;
    private String metricDefinitionSnapshotJson;
    private String rawMetricsJson;
    private Integer repeatCount;
    private String clientRequestId;
    private String status;
    private Integer totalSlots;
    private Integer terminalSlots;
    private Integer failedSlots;
    private Integer environmentFailures;
    private BigDecimal validityScore;
    private BigDecimal stabilityScore;
    private BigDecimal successRate;
    private BigDecimal latencyScore;
    private BigDecimal overallScore;
    private Long avgDurationMs;
    private Integer retryCount;
    private String errorMessage;
    private Long lastOperatedBy;
    private String lastOperation;
    private LocalDateTime lastOperatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
