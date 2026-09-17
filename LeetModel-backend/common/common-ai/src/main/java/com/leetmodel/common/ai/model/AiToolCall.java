package com.leetmodel.common.ai.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 模型提出的结构化工具调用。
 *
 * @param id 供应商工具调用标识
 * @param name 工具名称
 * @param argumentsJson 原始参数 JSON
 */
public record AiToolCall(
        @NotBlank String id,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String name,
        @NotBlank String argumentsJson
) {
}
