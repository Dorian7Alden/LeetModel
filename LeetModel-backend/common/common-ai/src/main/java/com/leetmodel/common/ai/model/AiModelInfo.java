package com.leetmodel.common.ai.model;

/**
 * 供应商模型摘要。
 *
 * @param id 模型标识
 * @param provider 供应商
 * @param ownedBy 模型所有者
 */
public record AiModelInfo(
        String id,
        AiProvider provider,
        String ownedBy
) {
}
