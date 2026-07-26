package com.leetmodel.user.service;

import com.leetmodel.common.api.dto.UserRoleDTO;

/**
 * 角色权限服务接口。
 *
 * @author LeetModel
 */
public interface RoleService {

    /**
     * 查询用户的角色和权限列表。
     *
     * @param userId 用户 ID
     * @return 角色 + 权限 DTO
     */
    UserRoleDTO getUserRoles(Long userId);
}
