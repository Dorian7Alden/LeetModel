package com.leetmodel.user.service;

import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.user.entity.Permission;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.PermissionMapper;
import com.leetmodel.user.mapper.RolePermissionMapper;
import com.leetmodel.user.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限管理服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private Permission permission;
    private PermissionRequest request;

    @BeforeEach
    void setUp() {
        permission = new Permission();
        permission.setId(1L);
        permission.setCode("user:read");
        permission.setName("查看用户");
        permission.setDescription("查看用户信息");
        permission.setCreateTime(LocalDateTime.now());
        permission.setUpdateTime(LocalDateTime.now());

        request = new PermissionRequest("problem:read", "查看题目", "查看题目信息");
    }

    @Test
    @DisplayName("获取权限列表成功")
    void listPermissionsSuccess() {
        when(permissionMapper.selectList(null)).thenReturn(List.of(permission));

        List<PermissionVO> permissions = permissionService.listPermissions();

        assertEquals(1, permissions.size());
        assertEquals("user:read", permissions.get(0).getCode());
        assertNotNull(permissions.get(0).getUpdateTime());
    }

    @Test
    @DisplayName("获取权限详情失败 —— 权限不存在")
    void getPermissionNotFound() {
        when(permissionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> permissionService.getPermissionById(999L)
        );

        assertEquals(UserErrorCode.PERMISSION_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("创建权限成功")
    void createPermissionSuccess() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.insert(any(Permission.class))).thenReturn(1);

        PermissionVO result = permissionService.createPermission(request);

        assertEquals("problem:read", result.getCode());
        assertEquals("查看题目", result.getName());
        verify(permissionMapper).insert(any(Permission.class));
    }

    @Test
    @DisplayName("创建权限失败 —— 权限编码重复")
    void createPermissionDuplicateCode() {
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> permissionService.createPermission(request)
        );

        assertEquals(UserErrorCode.PERMISSION_CODE_DUPLICATE.getCode(), exception.getCode());
        verify(permissionMapper, never()).insert(any(Permission.class));
    }

    @Test
    @DisplayName("更新权限成功")
    void updatePermissionSuccess() {
        when(permissionMapper.selectById(1L)).thenReturn(permission);
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.updateById(any(Permission.class))).thenReturn(1);

        PermissionVO result = permissionService.updatePermission(1L, request);

        assertEquals("problem:read", result.getCode());
        verify(permissionMapper).updateById(permission);
    }

    @Test
    @DisplayName("更新权限编码未变化时不重复校验")
    void updatePermissionWithSameCode() {
        PermissionRequest sameCodeRequest = new PermissionRequest("user:read", "查看用户资料", null);
        when(permissionMapper.selectById(1L)).thenReturn(permission);
        when(permissionMapper.updateById(any(Permission.class))).thenReturn(1);

        permissionService.updatePermission(1L, sameCodeRequest);

        verify(permissionMapper, never()).selectCount(any());
        verify(permissionMapper).updateById(permission);
    }

    @Test
    @DisplayName("删除权限成功")
    void deletePermissionSuccess() {
        when(permissionMapper.selectById(1L)).thenReturn(permission);
        when(rolePermissionMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.deleteById(1L)).thenReturn(1);

        permissionService.deletePermission(1L);

        verify(permissionMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除权限失败 —— 权限仍被角色使用")
    void deletePermissionInUse() {
        when(permissionMapper.selectById(1L)).thenReturn(permission);
        when(rolePermissionMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> permissionService.deletePermission(1L)
        );

        assertEquals(UserErrorCode.PERMISSION_IN_USE.getCode(), exception.getCode());
        verify(permissionMapper, never()).deleteById(1L);
    }
}
