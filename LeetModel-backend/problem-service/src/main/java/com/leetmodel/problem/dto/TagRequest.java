package com.leetmodel.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.leetmodel.problem.enums.TagType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建/更新标签请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagRequest {

    @NotBlank(message = "标签名称不能为空")
    private String name;

    @NotNull(message = "标签类型不能为空")
    private TagType type;
}
