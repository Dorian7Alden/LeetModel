package com.leetmodel.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色创建/更新请求。
 *
 * @author LeetModel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleRequest {

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 32, message = "角色编码最多32位")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 32, message = "角色名称最多32位")
    private String name;

    @Size(max = 128, message = "角色描述最多128位")
    private String description;
}
