package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.user.entity.*;
import com.leetmodel.user.mapper.*;
import com.leetmodel.user.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限服务实现 —— 通过五表联查获取用户的角色和权限列表。
 *
 * @author LeetModel
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public UserRoleDTO getUserRoles(Long userId) {
        // 1. 查用户拥有的所有角色 ID
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).toList();

        if (roleIds.isEmpty()) {
            return new UserRoleDTO(userId, Collections.emptyList(), Collections.emptyList());
        }

        // 2. 查角色编码
        List<String> roles = roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getCode).toList();

        // 3. 查角色拥有的权限 ID（去重）
        Set<Long> permissionIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds)
        ).stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());

        // 4. 查权限编码
        List<String> permissions = permissionIds.isEmpty()
                ? Collections.emptyList()
                : permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getCode).toList();

        return new UserRoleDTO(userId, roles, permissions);
    }
}
