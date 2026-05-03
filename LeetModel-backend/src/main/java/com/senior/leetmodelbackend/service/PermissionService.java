package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.PermissionMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.PermissionDTO;
import com.senior.leetmodelbackend.pojo.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;

    /**
     * 获取全部权限列表
     */
    public List<Permission> getPermissionList() {
        return permissionMapper.getAllPermissions();
    }

    /**
     * 根据 ID 查询权限，不存在则抛出 PERMISSION_NOT_FOUND
     */
    public Permission getPermissionById(Long permissionId) {
        Permission permission = permissionMapper.getPermissionById(permissionId);
        if (permission == null) {
            throw new BusinessException(ResponseCode.PERMISSION_NOT_FOUND);
        }
        return permission;
    }

    /**
     * 创建权限，code 重复时抛出 PERMISSION_CODE_DUPLICATE
     */
    public void createPermission(PermissionDTO dto) {
        Permission existing = permissionMapper.getPermissionByCode(dto.getCode());
        if (existing != null) {
            throw new BusinessException(ResponseCode.PERMISSION_CODE_DUPLICATE);
        }
        Permission permission = new Permission();
        permission.setName(dto.getName());
        permission.setCode(dto.getCode());
        permission.setDescription(dto.getDescription());
        permission.setStatus(dto.getStatus() != null ? dto.getStatus() : true);
        permissionMapper.insertPermission(permission);
        log.info("创建权限: {} [{}]", permission.getName(), permission.getCode());
    }

    /**
     * 更新权限，不存在则抛出 PERMISSION_NOT_FOUND，code 冲突则抛出 PERMISSION_CODE_DUPLICATE
     */
    public void updatePermission(Long permissionId, PermissionDTO dto) {
        getPermissionById(permissionId);
        Permission existing = permissionMapper.getPermissionByCode(dto.getCode());
        if (existing != null && !existing.getPermissionId().equals(permissionId)) {
            throw new BusinessException(ResponseCode.PERMISSION_CODE_DUPLICATE);
        }
        Permission permission = new Permission();
        permission.setPermissionId(permissionId);
        permission.setName(dto.getName());
        permission.setCode(dto.getCode());
        permission.setDescription(dto.getDescription());
        permission.setStatus(dto.getStatus());
        permissionMapper.updatePermission(permission);
        log.info("更新权限: {} [{}]", permission.getName(), permission.getCode());
    }

    /**
     * 删除权限，不存在则抛出 PERMISSION_NOT_FOUND
     */
    public void deletePermission(Long permissionId) {
        getPermissionById(permissionId);
        permissionMapper.deletePermission(permissionId);
        log.info("删除权限: {}", permissionId);
    }
}
