package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 管理聚合与版本对比使用的评价任务摘要。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationTaskSummaryDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;
    private String workflowVersion;
    private Integer repeatCount;
    private String status;
    private Integer totalSlots;
    private Integer terminalSlots;
    private Integer failedSlots;
    private BigDecimal validityScore;
    private BigDecimal stabilityScore;
    private BigDecimal successRate;
    private BigDecimal latencyScore;
    private BigDecimal overallScore;
    private Long avgDurationMs;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime finishedAt;
}
