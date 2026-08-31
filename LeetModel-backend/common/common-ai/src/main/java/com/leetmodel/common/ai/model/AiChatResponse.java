package com.leetmodel.common.ai.model;

import java.util.List;

/**
 * 统一 AI 对话响应。
 *
 * @param callId 网关调用标识
 * @param provider 实际供应商
 * @param model 实际模型
 * @param providerResponseId 供应商响应标识
 * @param content 回答内容
 * @param reasoningContent 思考内容
 * @param finishReason 停止原因
 * @param usage Token 用量
 * @param cost 调用费用；同步响应不可得时为 null 或 UNKNOWN
 * @param toolCalls 模型提出的工具调用
 */
public record AiChatResponse(
        String callId,
        AiProvider provider,
        String model,
        String providerResponseId,
        String content,
        String reasoningContent,
        String finishReason,
        AiUsage usage,
        AiCost cost,
        List<AiToolCall> toolCalls
) {
    public AiChatResponse(String callId, AiProvider provider, String model, String providerResponseId,
                          String content, String reasoningContent, String finishReason,
                          AiUsage usage, AiCost cost) {
        this(callId, provider, model, providerResponseId, content, reasoningContent,
                finishReason, usage, cost, null);
    }

    public AiChatResponse(String callId, AiProvider provider, String model, String providerResponseId,
                          String content, String reasoningContent, String finishReason, AiUsage usage) {
        this(callId, provider, model, providerResponseId, content, reasoningContent,
                finishReason, usage, null, null);
    }
}
