package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 隔离评审实验结果，不写入用户正式评审任务。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewExperimentResultDTO {
    private Long submissionId;
    private Long problemId;
    private String workflowVersion;
    private String status;
    private String failureType;
    private BigDecimal score;
    private String resultJson;
    private String modelName;
    private String aiCallId;
    private Long durationMs;
    private String errorMessage;
}
