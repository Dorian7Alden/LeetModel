package com.leetmodel.common.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * AI 对话消息。
 *
 * @param role 消息角色
 * @param content 有序内容块
 */
public record AiMessage(
        @NotNull AiRole role,
        @NotEmpty List<@Valid AiContentPart> content
) {
}
