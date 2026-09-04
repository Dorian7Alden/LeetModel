package com.leetmodel.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_task_log")
public class ReviewTaskLog extends BaseEntity {
    private Long taskId;
    private String workflowVersion;
    private Integer attemptNo;
    private String stepCode;
    private String stepName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String inputSummary;
    private String outputSummary;
    private String aiCallId;
    private String errorMessage;
}
