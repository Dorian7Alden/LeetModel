package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 一次客服回复内部的工具调用执行事实。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_tool_call")
public class AssistantToolCall extends BaseEntity {
    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Integer attemptNo;
    private Integer sequenceNo;
    private String providerToolCallId;
    private String toolsetVersion;
    private String toolName;
    private String toolVersion;
    private String status;
    private String argumentsJson;
    private String resultSnapshotJson;
    private String planningAiCallId;
    private String nestedAiCallId;
    private String errorCode;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
