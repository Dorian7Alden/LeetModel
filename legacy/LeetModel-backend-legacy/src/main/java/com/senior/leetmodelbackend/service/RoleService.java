package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.mapper.RoleMapper;
import com.senior.leetmodelbackend.pojo.dto.admin.RoleDTO;
import com.senior.leetmodelbackend.pojo.entity.Permission;
import com.senior.leetmodelbackend.pojo.entity.Role;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class RoleService {

    private final RoleMapper roleMapper;

    /**
     * 获取全部角色列表
     */
    public List<Role> getRoleList() {
        return roleMapper.getAllRoles();
    }

    /**
     * 根据 ID 查询角色，不存在则抛出 ROLE_NOT_FOUND
     */
    public Role getRoleById(Integer roleId) {
        Role role = roleMapper.getRoleById(roleId);
        if (role == null) {
            throw new BusinessException(ResponseCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    /**
     * 创建角色，code 重复时抛出 ROLE_CODE_DUPLICATE
     */
    public void createRole(RoleDTO dto) {
        Role existing = roleMapper.getRoleByCode(dto.getCode());
        if (existing != null) {
            throw new BusinessException(ResponseCode.ROLE_CODE_DUPLICATE);
        }
        Role role = new Role();
        role.setName(dto.getName());
        role.setCode(dto.getCode());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() != null ? dto.getStatus() : true);
        roleMapper.insertRole(role);
        log.info("创建角色: {} [{}]", role.getName(), role.getCode());
    }

    /**
     * 更新角色，不存在则抛出 ROLE_NOT_FOUND，code 冲突则抛出 ROLE_CODE_DUPLICATE
     */
    public void updateRole(Integer roleId, RoleDTO dto) {
        getRoleById(roleId);
        Role existing = roleMapper.getRoleByCode(dto.getCode());
        if (existing != null && !existing.getRoleId().equals(roleId)) {
            throw new BusinessException(ResponseCode.ROLE_CODE_DUPLICATE);
        }
        Role role = new Role();
        role.setRoleId(roleId);
        role.setName(dto.getName());
        role.setCode(dto.getCode());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus());
        roleMapper.updateRole(role);
        log.info("更新角色: {} [{}]", role.getName(), role.getCode());
    }

    /**
     * 删除角色，不存在则抛出 ROLE_NOT_FOUND
     */
    public void deleteRole(Integer roleId) {
        getRoleById(roleId);
        roleMapper.deleteRole(roleId);
        log.info("删除角色: {}", roleId);
    }

    /**
     * 查询角色持有的权限列表，角色不存在则抛出 ROLE_NOT_FOUND
     */
    public List<Permission> getRolePermissions(Integer roleId) {
        getRoleById(roleId);
        return roleMapper.getPermissionsByRoleId(roleId);
    }

    /**
     * 分配角色权限（先删后增），角色不存在则抛出 ROLE_NOT_FOUND
     */
    @Transactional
    public void assignRolePermissions(Integer roleId, List<Integer> permissionIds) {
        getRoleById(roleId);
        roleMapper.deleteRolePermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Integer permissionId : permissionIds) {
                roleMapper.insertRolePermission(roleId, permissionId);
            }
        }
        log.info("分配角色权限: roleId={}, permissionIds={}", roleId, permissionIds);
    }
}
