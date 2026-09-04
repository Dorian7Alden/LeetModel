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
     * @param userId 目标用户 ID，不能为 null
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
     * @param roleId 目标角色 ID，不能为 null
     * @return 角色 VO
     * @throws com.leetmodel.common.core.exception.BusinessException 若角色不存在
     */
    RoleVO getRoleById(Long roleId);

    /**
     * 创建角色。
     *
     * @param request 角色信息请求对象，不能为 null
     * @return 创建后的角色 VO
     * @throws com.leetmodel.common.core.exception.BusinessException 若角色编码已存在
     */
    RoleVO createRole(RoleRequest request);

    /**
     * 更新角色。
     *
     * @param roleId  目标角色 ID，不能为 null
     * @param request 包含修改信息的请求对象，不能为 null
     * @return 更新后的角色 VO
     * @throws com.leetmodel.common.core.exception.BusinessException 若角色不存在、编码重复或尝试修改预设角色
     */
    RoleVO updateRole(Long roleId, RoleRequest request);

    /**
     * 删除角色。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @throws com.leetmodel.common.core.exception.BusinessException 若角色不存在或尝试删除预设角色
     */
    void deleteRole(Long roleId);

    /**
     * 获取角色拥有的权限。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 权限列表
     * @throws com.leetmodel.common.core.exception.BusinessException 若角色不存在
     */
    List<PermissionVO> getRolePermissions(Long roleId);

    /**
     * 全量更新角色权限。
     *
     * @param roleId        目标角色 ID，不能为 null
     * @param permissionIds 权限 ID 列表，不能为 null
     * @throws com.leetmodel.common.core.exception.BusinessException 若角色不存在或部分权限 ID 不存在
     */
    void updateRolePermissions(Long roleId, List<Long> permissionIds);

}
