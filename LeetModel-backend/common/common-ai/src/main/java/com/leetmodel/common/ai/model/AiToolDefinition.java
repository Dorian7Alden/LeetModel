package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 供应商无关的 AI 工具定义。
 *
 * @param type 工具类型
 * @param name 工具名称
 * @param description 工具用途描述
 * @param inputSchema JSON Object 输入 Schema
 */
public record AiToolDefinition(
        @NotNull AiToolType type,
        @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String name,
        @NotBlank @Size(max = 1000) String description,
        @NotNull Map<String, Object> inputSchema
) {
    /**
     * 校验首版工具类型和 Schema 边界。
     *
     * @return 是否为无远程引用的 JSON Object Schema
     */
    @AssertTrue(message = "首版工具必须是 FUNCTION，输入 Schema 必须是 JSON Object 且不能包含远程引用")
    @JsonIgnore
    public boolean isSchemaValid() {
        if (type == null || inputSchema == null) return true;
        return type == AiToolType.FUNCTION
                && "object".equals(inputSchema.get("type"))
                && !containsRemoteReference(inputSchema);
    }

    /**
     * 递归检查 Schema 中的远程引用。
     *
     * @param value 当前节点
     * @return 是否包含远程引用
     */
    private boolean containsRemoteReference(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object reference = map.get("$ref");
            if (reference instanceof String text && !text.startsWith("#/")) return true;
            for (Object child : map.values()) {
                if (containsRemoteReference(child)) return true;
            }
        }
        if (value instanceof List<?> list) {
            for (Object child : list) {
                if (containsRemoteReference(child)) return true;
            }
        }
        return false;
    }
}
