package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评审任务和结果的跨服务只读摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryDTO {
    private Long taskId;
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private String status;
    private String workflowVersion;
    private BigDecimal score;
    private String resultJson;
    private String modelName;
    private String aiCallId;
    private String errorMessage;
    private LocalDateTime finishedAt;
}
