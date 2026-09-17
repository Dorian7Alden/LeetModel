package com.leetmodel.assistant.tool;

import com.leetmodel.common.ai.model.AiChatResponse;

/** 一次受控工具循环的最终回答和供消息标记使用的最小工具上下文。 */
public record AssistantToolRunResult(
        AiChatResponse response,
        String toolContextJson,
        int executedToolCalls) {
}
