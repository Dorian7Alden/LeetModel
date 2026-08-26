package com.leetmodel.suggestion.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.leetmodel.suggestion.workflow.SuggestionV1Output;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SuggestionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewTaskId;

    private String workflowVersion;
    private String reviewWorkflowVersion;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private SuggestionV1Output result;
    private String modelName;
    private String aiCallId;
    private LocalDateTime createTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
