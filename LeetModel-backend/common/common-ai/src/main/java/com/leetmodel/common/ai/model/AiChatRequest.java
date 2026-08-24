package com.leetmodel.common.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 统一 AI 对话请求。
 *
 * @param scene 业务场景
 * @param messages 对话消息
 * @param maxTokens 最大输出 Token
 * @param temperature 采样温度
 * @param responseFormat 响应格式
 * @param thinkingEnabled 是否启用思考
 */
public record AiChatRequest(
        @NotNull AiScene scene,
        @NotEmpty List<@Valid AiMessage> messages,
        @Min(1) @Max(131072) Integer maxTokens,
        Double temperature,
        AiResponseFormat responseFormat,
        Boolean thinkingEnabled
) {
}
