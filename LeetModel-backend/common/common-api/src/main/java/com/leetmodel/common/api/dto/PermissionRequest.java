package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限创建和更新请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequest {

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 64, message = "权限编码最多64位")
    @Pattern(
            regexp = "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$",
            message = "权限编码必须使用资源:动作格式"
    )
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称最多64位")
    private String name;

    @Size(max = 128, message = "权限描述最多128位")
    private String description;
}
