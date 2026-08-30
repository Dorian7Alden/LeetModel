package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 模型工具选择约束。
 *
 * @param type 选择方式
 * @param name 指定工具名，仅 NAMED 使用
 */
public record AiToolChoice(
        @NotNull AiToolChoiceType type,
        @Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String name
) {
    /**
     * 校验指定工具名与选择方式一致。
     *
     * @return 是否合法
     */
    @AssertTrue(message = "NAMED 必须提供工具名，其他工具选择方式不能提供工具名")
    @JsonIgnore
    public boolean isNameValid() {
        if (type == null) return true;
        return type == AiToolChoiceType.NAMED
                ? name != null && !name.isBlank()
                : name == null;
    }
}
