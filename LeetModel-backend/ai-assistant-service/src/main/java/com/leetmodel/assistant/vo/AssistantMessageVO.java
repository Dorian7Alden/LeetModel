package com.leetmodel.assistant.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AssistantMessageVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToMessageId;
    private String role;
    private String status;
    private String productionConfigVersion;
    private Long productionRevision;
    private String workflowVersion;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String toolsetVersion;
    private Integer attemptCount;
    private String ragMode;
    private String ragIndexVersion;
    private String content;
    private String errorMessage;
    private String modelName;
    private String aiCallId;
    private Boolean usedTool;
    private Boolean usedProblemTool;
    private LocalDateTime createTime;
}
