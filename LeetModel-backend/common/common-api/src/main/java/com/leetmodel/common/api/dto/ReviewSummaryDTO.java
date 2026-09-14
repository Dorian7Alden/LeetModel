package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    private BigDecimal score;
    private String resultJson;
    private String modelName;
    private String aiCallId;
    private String errorMessage;
    private LocalDateTime finishedAt;
}
