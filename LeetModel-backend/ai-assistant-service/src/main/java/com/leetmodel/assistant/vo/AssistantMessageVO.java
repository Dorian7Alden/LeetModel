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
    private String content;
    private String errorMessage;
    private String modelName;
    private String aiCallId;
    private Boolean usedProblemTool;
    private LocalDateTime createTime;
}
