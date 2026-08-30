package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * AI 对话消息。
 *
 * @param role 消息角色
 * @param content 有序内容块
 * @param toolCalls 助手提出的工具调用
 * @param toolCallId 工具结果关联标识
 * @param toolName 工具结果对应名称
 */
public record AiMessage(
        @NotNull AiRole role,
        @NotNull List<@Valid AiContentPart> content,
        @Valid List<@Valid AiToolCall> toolCalls,
        String toolCallId,
        String toolName
) {
    /** 旧消息构造器，保持无工具调用方源码兼容。 */
    public AiMessage(AiRole role, List<AiContentPart> content) {
        this(role, content, null, null, null);
    }

    /**
     * 校验普通消息、工具请求消息和工具结果消息的互斥结构。
     *
     * @return 消息结构是否合法
     */
    @AssertTrue(message = "AI 消息的角色、内容和工具关联字段不匹配")
    @JsonIgnore
    public boolean isStructureValid() {
        if (role == null || content == null) return true;
        boolean hasContent = !content.isEmpty();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        boolean hasToolCallId = toolCallId != null && !toolCallId.isBlank();
        boolean hasToolName = toolName != null && !toolName.isBlank();
        if (role == AiRole.ASSISTANT) {
            return (hasContent || hasToolCalls) && !hasToolCallId && !hasToolName;
        }
        if (role == AiRole.TOOL) {
            return hasContent && !hasToolCalls && hasToolCallId && hasToolName;
        }
        return hasContent && !hasToolCalls && !hasToolCallId && !hasToolName;
    }
}
