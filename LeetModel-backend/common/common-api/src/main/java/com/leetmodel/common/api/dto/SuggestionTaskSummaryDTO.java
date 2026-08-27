package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理聚合使用的论文建议任务摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionTaskSummaryDTO {
    private Long taskId;
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private String status;
    private String workflowVersion;
    private String modelName;
    private String aiCallId;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime finishedAt;
}
