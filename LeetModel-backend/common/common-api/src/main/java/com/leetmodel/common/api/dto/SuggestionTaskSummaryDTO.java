package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private String status;
    private String workflowVersion;
    private String modelName;
    private String aiCallId;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime finishedAt;
}
