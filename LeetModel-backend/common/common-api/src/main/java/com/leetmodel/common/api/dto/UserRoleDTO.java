package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户角色与权限 DTO —— user 服务通过 Feign 返回给 common-security。
 *
 * @author LeetModel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleDTO {

    /** 用户 ID */
    private Long userId;

    /** 角色列表：["admin"] / ["vip"] / ["user"] */
    private List<String> roles;

    /** 权限列表：["user:read", "submission:create"] */
    private List<String> permissions;
}
