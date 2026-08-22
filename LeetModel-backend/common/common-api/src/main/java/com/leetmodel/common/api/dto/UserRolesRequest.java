package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户角色更新请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesRequest {

    @NotNull(message = "角色列表不能为空")
    @Size(min = 1, message = "至少分配一个角色")
    private List<Long> roleIds;
}
