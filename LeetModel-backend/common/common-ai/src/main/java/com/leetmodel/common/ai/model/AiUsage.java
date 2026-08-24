package com.leetmodel.common.ai.model;

/**
 * 统一 AI 用量。
 *
 * @param promptTokens 输入 Token
 * @param cacheHitTokens 缓存命中输入 Token
 * @param cacheMissTokens 缓存未命中输入 Token
 * @param completionTokens 输出 Token
 * @param reasoningTokens 推理 Token
 * @param totalTokens 总 Token
 * @param complete 用量是否完整
 */
public record AiUsage(
        Long promptTokens,
        Long cacheHitTokens,
        Long cacheMissTokens,
        Long completionTokens,
        Long reasoningTokens,
        Long totalTokens,
        boolean complete
) {
}
