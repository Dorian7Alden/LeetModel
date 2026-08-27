package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 评价任务中一个样本重复槽位的最新运行尝试。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRunDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long runId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sampleId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    private Integer repetitionNo;
    private Integer attemptNo;
    private String status;
    private String failureType;
    private BigDecimal score;
    private String modelName;
    private String aiCallId;
    private Long durationMs;
    private String errorMessage;
}
