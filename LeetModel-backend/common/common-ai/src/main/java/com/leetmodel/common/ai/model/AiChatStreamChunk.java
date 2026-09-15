package com.leetmodel.common.ai.model;

/**
 * AI 对话流式增量块。
 *
 * @param callId 统一调用标识
 * @param deltaText 增量文本内容，若本帧仅包含状态或元数据则可为空
 * @param finishReason 结束原因，未结束时为 null
 * @param completedResponse 结束时的完整聚合响应对象（包含最终 content、usage、cost），未结束时为 null
 */
public record AiChatStreamChunk(
        String callId,
        String deltaText,
        String finishReason,
        AiChatResponse completedResponse
) {}
