package com.leetmodel.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 更新赛事基础数据的管理请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContestRequest {

    @NotBlank(message = "赛事编码不能为空")
    @Size(max = 32, message = "赛事编码不能超过32个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "赛事编码只能包含字母、数字、下划线和短横线")
    private String code;

    @NotBlank(message = "赛事名称不能为空")
    @Size(max = 100, message = "赛事名称不能超过100个字符")
    private String name;
}
