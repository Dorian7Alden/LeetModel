package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.PermissionMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.PermissionDTO;
import com.senior.leetmodelbackend.pojo.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;

    public List<Permission> getPermissionList() {
        return permissionMapper.getAllPermissions();
    }

    public Permission getPermissionById(Long permissionId) {
        Permission permission = permissionMapper.getPermissionById(permissionId);
        if (permission == null) {
            throw new BusinessException(ResponseCode.PERMISSION_NOT_FOUND);
        }
        return permission;
    }

    public void createPermission(PermissionDTO dto) {
        Permission existing = permissionMapper.getPermissionByCode(dto.getCode());
        if (existing != null) {
            throw new BusinessException(ResponseCode.PERMISSION_CODE_DUPLICATE);
        }
        Permission permission = new Permission();
        BeanUtils.copyProperties(dto, permission);
        if (permission.getStatus() == null) {
            permission.setStatus(true);
        }
        permissionMapper.insertPermission(permission);
        log.info("创建权限: {} [{}]", permission.getName(), permission.getCode());
    }

    public void updatePermission(Long permissionId, PermissionDTO dto) {
        getPermissionById(permissionId);
        Permission existing = permissionMapper.getPermissionByCode(dto.getCode());
        if (existing != null && !existing.getPermissionId().equals(permissionId)) {
            throw new BusinessException(ResponseCode.PERMISSION_CODE_DUPLICATE);
        }
        Permission permission = new Permission();
        BeanUtils.copyProperties(dto, permission);
        permission.setPermissionId(permissionId);
        permissionMapper.updatePermission(permission);
        log.info("更新权限: {} [{}]", permission.getName(), permission.getCode());
    }

    public void deletePermission(Long permissionId) {
        getPermissionById(permissionId);
        permissionMapper.deletePermission(permissionId);
        log.info("删除权限: {}", permissionId);
    }
}
