package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.RoleMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.RoleDTO;
import com.senior.leetmodelbackend.pojo.entity.Permission;
import com.senior.leetmodelbackend.pojo.entity.Role;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;

    public List<Role> getRoleList() {
        return roleMapper.getAllRoles();
    }

    public Role getRoleById(Long roleId) {
        Role role = roleMapper.getRoleById(roleId);
        if (role == null) {
            throw new BusinessException(ResponseCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    public void createRole(RoleDTO dto) {
        Role existing = roleMapper.getRoleByCode(dto.getCode());
        if (existing != null) {
            throw new BusinessException(ResponseCode.ROLE_CODE_DUPLICATE);
        }
        Role role = new Role();
        BeanUtils.copyProperties(dto, role);
        if (role.getStatus() == null) {
            role.setStatus(true);
        }
        roleMapper.insertRole(role);
        log.info("创建角色: {} [{}]", role.getName(), role.getCode());
    }

    public void updateRole(Long roleId, RoleDTO dto) {
        getRoleById(roleId);
        Role existing = roleMapper.getRoleByCode(dto.getCode());
        if (existing != null && !existing.getRoleId().equals(roleId)) {
            throw new BusinessException(ResponseCode.ROLE_CODE_DUPLICATE);
        }
        Role role = new Role();
        BeanUtils.copyProperties(dto, role);
        role.setRoleId(roleId);
        roleMapper.updateRole(role);
        log.info("更新角色: {} [{}]", role.getName(), role.getCode());
    }

    public void deleteRole(Long roleId) {
        getRoleById(roleId);
        roleMapper.deleteRole(roleId);
        log.info("删除角色: {}", roleId);
    }

    public List<Permission> getRolePermissions(Long roleId) {
        getRoleById(roleId);
        return roleMapper.getPermissionsByRoleId(roleId);
    }

    @Transactional
    public void assignRolePermissions(Long roleId, List<Long> permissionIds) {
        getRoleById(roleId);
        roleMapper.deleteRolePermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                roleMapper.insertRolePermission(roleId, permissionId);
            }
        }
        log.info("分配角色权限: roleId={}, permissionIds={}", roleId, permissionIds);
    }
}
