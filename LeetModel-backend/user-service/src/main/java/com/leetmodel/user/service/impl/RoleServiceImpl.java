package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.user.entity.Permission;
import com.leetmodel.user.entity.Role;
import com.leetmodel.user.entity.RolePermission;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.PermissionMapper;
import com.leetmodel.user.mapper.RoleMapper;
import com.leetmodel.user.mapper.RolePermissionMapper;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.RoleService;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色权限服务实现 —— 通过五表联查获取用户的角色和权限列表。
 */
@Slf4j
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

    // ==================== 角色 CRUD ====================

    @Override
    public List<RoleVO> listRoles() {
        return roleMapper.selectList(null).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public RoleVO getRoleById(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        BusinessException.throwIf(role == null, UserErrorCode.ROLE_NOT_FOUND);
        return toVO(role);
    }

    @Override
    @Transactional
    public RoleVO createRole(RoleRequest request) {
        // 校验编码唯一
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getCode, request.getCode());
        BusinessException.throwIf(roleMapper.selectCount(wrapper) > 0, UserErrorCode.ROLE_CODE_DUPLICATE);

        Role role = new Role();
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        roleMapper.insert(role);

        log.info("创建角色: {} ({})", role.getCode(), role.getId());
        return toVO(role);
    }

    @Override
    @Transactional
    public RoleVO updateRole(Long roleId, RoleRequest request) {
        Role role = roleMapper.selectById(roleId);
        BusinessException.throwIf(role == null, UserErrorCode.ROLE_NOT_FOUND);

        // 编码变更时校验唯一
        if (!role.getCode().equals(request.getCode())) {
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Role::getCode, request.getCode());
            BusinessException.throwIf(roleMapper.selectCount(wrapper) > 0, UserErrorCode.ROLE_CODE_DUPLICATE);
        }

        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        roleMapper.updateById(role);

        log.info("更新角色: {} ({})", role.getCode(), roleId);
        return toVO(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        BusinessException.throwIf(role == null, UserErrorCode.ROLE_NOT_FOUND);

        // 删除关联数据
        LambdaQueryWrapper<RolePermission> rpWrapper = new LambdaQueryWrapper<>();
        rpWrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(rpWrapper);

        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getRoleId, roleId);
        userRoleMapper.delete(urWrapper);

        roleMapper.deleteById(roleId);
        log.info("删除角色: {} ({})", role.getCode(), roleId);
    }

    // ==================== 权限列表 ====================

    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(null).stream()
                .map(p -> PermissionVO.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .name(p.getName())
                        .description(p.getDescription())
                        .createTime(p.getCreateTime())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    private RoleVO toVO(Role role) {
        return RoleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }
}
