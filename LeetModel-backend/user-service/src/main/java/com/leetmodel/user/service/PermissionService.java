package com.leetmodel.user.service;

import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.vo.PermissionVO;

import java.util.List;

/**
 * 权限管理服务接口。
 */
public interface PermissionService {

    /**
     * 查询系统中全部细粒度权限定义列表。
     *
     * @return 权限视图对象列表
     */
    List<PermissionVO> listPermissions();

    /**
     * 根据权限 ID 查询权限详情。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 权限视图对象
     * @throws com.leetmodel.common.core.exception.BusinessException 若权限不存在
     */
    PermissionVO getPermissionById(Long permissionId);

    /**
     * 创建新的系统权限定义。
     *
     * @param request 权限信息请求对象，不能为 null
     * @return 创建成功后的权限视图对象
     * @throws com.leetmodel.common.core.exception.BusinessException 若权限编码重复
     */
    PermissionVO createPermission(PermissionRequest request);

    /**
     * 修改已有权限的编码、名称与描述。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @param request      包含待修改信息的请求对象，不能为 null
     * @return 更新后的权限视图对象
     * @throws com.leetmodel.common.core.exception.BusinessException 若权限不存在或编码重复
     */
    PermissionVO updatePermission(Long permissionId, PermissionRequest request);

    /**
     * 删除未被任何角色引用的权限定义。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @throws com.leetmodel.common.core.exception.BusinessException 若权限不存在或仍被角色引用
     */
    void deletePermission(Long permissionId);
}
