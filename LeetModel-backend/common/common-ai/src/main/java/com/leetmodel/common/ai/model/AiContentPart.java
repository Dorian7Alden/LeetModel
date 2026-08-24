package com.leetmodel.common.ai.model;

import jakarta.validation.constraints.NotNull;

/**
 * AI 消息中的一个有序内容块。
 *
 * @param type 内容类型
 * @param text 文本内容
 * @param url 图片地址
 */
public record AiContentPart(
        @NotNull AiContentType type,
        String text,
        String url
) {
}
