package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_run_attempt")
public class EvaluationRunAttempt extends BaseEntity {
    private Long taskId;
    private Long sampleId;
    private Integer repetitionNo;
    private Integer attemptNo;
    private String slotKey;
    private String experimentRunId;
    private String status;
    private String failureType;
    private BigDecimal score;
    private String resultJson;
    private String metricsJson;
    private String modelName;
    private String modelExecutionConfigVersion;
    private String ragIndexVersion;
    private String aiCallId;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
