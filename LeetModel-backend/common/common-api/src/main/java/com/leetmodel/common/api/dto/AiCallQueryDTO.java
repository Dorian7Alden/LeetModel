package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 调用审计查询条件。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCallQueryDTO {
    @Size(max = 30, message = "调用场景不能超过30个字符")
    private String scene;

    @Size(max = 30, message = "供应商不能超过30个字符")
    private String provider;

    @Size(max = 100, message = "模型名称不能超过100个字符")
    private String model;

    @Size(max = 20, message = "调用状态不能超过20个字符")
    private String status;

    @Min(value = 1, message = "查询数量不能小于1")
    @Max(value = 100, message = "查询数量不能超过100")
    private Integer limit = 20;
}
