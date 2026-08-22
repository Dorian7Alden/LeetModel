package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 角色权限全量更新请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionsRequest {

    @NotNull(message = "权限列表不能为空")
    private List<@NotNull(message = "权限 ID 不能为空") Long> permissionIds;
}
