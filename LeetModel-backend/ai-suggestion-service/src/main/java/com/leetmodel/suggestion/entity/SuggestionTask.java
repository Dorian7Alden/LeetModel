package com.leetmodel.suggestion.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("suggestion_task")
public class SuggestionTask extends BaseEntity {
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private Long reviewTaskId;
    private String workflowVersion;
    private String reviewWorkflowVersion;
    private String status;
    private String promptSnapshot;
    private Integer retryCount;
    private LocalDateTime nextRunAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private String resultJson;
    private String modelName;
    private String aiCallId;
}
