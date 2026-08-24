package com.leetmodel.problem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContestRequest {
    @NotBlank(message = "赛事编码不能为空")
    @Size(max = 32, message = "赛事编码不能超过 32 个字符")
    private String code;
    @NotBlank(message = "赛事名称不能为空")
    @Size(max = 100, message = "赛事名称不能超过 100 个字符")
    private String name;
    @Min(value = 0, message = "赛事状态只能为 0 或 1")
    @Max(value = 1, message = "赛事状态只能为 0 或 1")
    private Integer status;
}
