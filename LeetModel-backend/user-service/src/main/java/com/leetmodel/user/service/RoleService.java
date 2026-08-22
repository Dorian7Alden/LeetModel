package com.leetmodel.user.service;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;

import java.util.List;

/**
 * 角色权限服务接口。
 */
public interface RoleService {

    /**
     * 查询用户的角色和权限列表。
     *
     * @param userId 用户 ID
     * @return 角色 + 权限 DTO
     */
    UserRoleDTO getUserRoles(Long userId);

    /**
     * 获取角色列表。
     *
     * @return 角色 VO 列表
     */
    List<RoleVO> listRoles();

    /**
     * 获取角色详情。
     *
     * @param roleId 角色 ID
     * @return 角色 VO
     */
    RoleVO getRoleById(Long roleId);

    /**
     * 创建角色。
     *
     * @param request 角色信息
     * @return 创建后的角色 VO
     */
    RoleVO createRole(RoleRequest request);

    /**
     * 更新角色。
     *
     * @param roleId  角色 ID
     * @param request 更新信息
     * @return 更新后的角色 VO
     */
    RoleVO updateRole(Long roleId, RoleRequest request);

    /**
     * 删除角色。
     *
     * @param roleId 角色 ID
     */
    void deleteRole(Long roleId);

    /**
     * 获取角色拥有的权限。
     * @param roleId 角色 ID
     * @return 权限列表
     */
    List<PermissionVO> getRolePermissions(Long roleId);

    /**
     * 全量更新角色权限。
     * @param roleId 角色 ID
     * @param permissionIds 权限 ID 列表
     */
    void updateRolePermissions(Long roleId, List<Long> permissionIds);

}
