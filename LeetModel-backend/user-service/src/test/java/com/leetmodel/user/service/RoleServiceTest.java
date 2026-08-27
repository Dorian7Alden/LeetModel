package com.leetmodel.user.service;

import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.user.entity.Permission;
import com.leetmodel.user.entity.Role;
import com.leetmodel.user.entity.RolePermission;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.PermissionMapper;
import com.leetmodel.user.mapper.RoleMapper;
import com.leetmodel.user.mapper.RolePermissionMapper;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.impl.RoleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色与授权关系服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Test
    @DisplayName("聚合用户角色和权限成功")
    void getUserRolesSuccess() {
        UserRole userRole = new UserRole();
        userRole.setUserId(10L);
        userRole.setRoleId(1L);
        Role role = role(1L, "admin");
        RolePermission first = rolePermission(1L, 1L);
        RolePermission duplicate = rolePermission(1L, 1L);
        Permission permission = permission(1L, "user:read");

        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(first, duplicate));
        when(permissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(permission));

        UserRoleDTO result = roleService.getUserRoles(10L);

        assertEquals(List.of("admin"), result.getRoles());
        assertEquals(List.of("user:read"), result.getPermissions());
    }

    @Test
    @DisplayName("获取角色权限成功")
    void getRolePermissionsSuccess() {
        Role role = role(1L, "admin");
        RolePermission rolePermission = rolePermission(1L, 1L);
        Permission permission = permission(1L, "user:read");
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission));
        when(permissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(permission));

        List<PermissionVO> result = roleService.getRolePermissions(1L);

        assertEquals(1, result.size());
        assertEquals("user:read", result.get(0).getCode());
    }

    @Test
    @DisplayName("全量更新角色权限成功并去重")
    void updateRolePermissionsSuccessAndDeduplicate() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "admin"));
        when(permissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                permission(1L, "user:read"),
                permission(2L, "user:update")
        ));

        roleService.updateRolePermissions(1L, List.of(1L, 2L, 1L));

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, times(2)).insert(any(RolePermission.class));
    }

    @Test
    @DisplayName("全量更新角色权限失败 —— 权限不存在")
    void updateRolePermissionsPermissionNotFound() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "admin"));
        when(permissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                permission(1L, "user:read")
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.updateRolePermissions(1L, List.of(1L, 999L))
        );

        assertEquals(UserErrorCode.PERMISSION_NOT_FOUND.getCode(), exception.getCode());
        verify(rolePermissionMapper, never()).delete(any());
        verify(rolePermissionMapper, never()).insert(any(RolePermission.class));
    }

    @Test
    @DisplayName("全量更新角色权限失败 —— 角色不存在")
    void updateRolePermissionsRoleNotFound() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.updateRolePermissions(999L, List.of(1L))
        );

        assertEquals(UserErrorCode.ROLE_NOT_FOUND.getCode(), exception.getCode());
        verify(permissionMapper, never()).selectBatchIds(anyCollection());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    @DisplayName("角色可以清空全部权限")
    void updateRolePermissionsAllowsEmptyList() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "custom"));

        roleService.updateRolePermissions(2L, List.of());

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, never()).insert(any(RolePermission.class));
    }

    @Test
    @DisplayName("删除系统角色被拒绝")
    void deleteSystemRoleRejected() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, "user"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.deleteRole(3L)
        );

        assertEquals(UserErrorCode.SYSTEM_ROLE_PROTECTED.getCode(), exception.getCode());
        verify(roleMapper, never()).deleteById(3L);
    }

    @Test
    @DisplayName("修改系统角色编码被拒绝")
    void updateSystemRoleCodeRejected() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "admin"));
        RoleRequest request = new RoleRequest("manager", "管理员", null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.updateRole(1L, request)
        );

        assertEquals(UserErrorCode.SYSTEM_ROLE_PROTECTED.getCode(), exception.getCode());
        verify(roleMapper, never()).updateById(any(Role.class));
    }

    @Test
    @DisplayName("创建角色失败 —— 角色编码重复")
    void createRoleDuplicateCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);
        RoleRequest request = new RoleRequest("admin", "另一个管理员", null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.createRole(request)
        );

        assertEquals(UserErrorCode.ROLE_CODE_DUPLICATE.getCode(), exception.getCode());
        verify(roleMapper, never()).insert(any(Role.class));
    }

    /**
     * 创建角色实体。
     * @param id 角色 ID
     * @param code 角色编码
     * @return 角色实体
     */
    private Role role(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        return role;
    }

    /**
     * 创建权限实体。
     * @param id 权限 ID
     * @param code 权限编码
     * @return 权限实体
     */
    private Permission permission(Long id, String code) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName(code);
        return permission;
    }

    /**
     * 创建角色权限关联实体。
     * @param roleId 角色 ID
     * @param permissionId 权限 ID
     * @return 角色权限关联
     */
    private RolePermission rolePermission(Long roleId, Long permissionId) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        return rolePermission;
    }
}
