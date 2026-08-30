package com.leetmodel.assistant.tool;

import com.leetmodel.assistant.workflow.AssistantProductionSnapshot;

import java.time.Instant;

/** 只由服务端构造、不会暴露给模型的工具执行身份和版本上下文。 */
public record AssistantToolExecutionContext(
        Long userId,
        Long conversationId,
        Long userMessageId,
        Long assistantMessageId,
        int attemptNo,
        int sequenceNo,
        String toolsetVersion,
        AssistantProductionSnapshot productionSnapshot,
        Instant deadline) {
}
