package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户状态修改请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusRequest {

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值只能为0或1")
    @Max(value = 1, message = "状态值只能为0或1")
    private Integer status;
}
