package com.leetmodel.user.service;

import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.vo.PermissionVO;

import java.util.List;

/**
 * 权限管理服务接口。
 */
public interface PermissionService {

    /**
     * 获取权限列表。
     * @return 权限列表
     */
    List<PermissionVO> listPermissions();

    /**
     * 获取权限详情。
     * @param permissionId 权限 ID
     * @return 权限详情
     */
    PermissionVO getPermissionById(Long permissionId);

    /**
     * 创建权限。
     * @param request 权限信息
     * @return 创建后的权限
     */
    PermissionVO createPermission(PermissionRequest request);

    /**
     * 更新权限。
     * @param permissionId 权限 ID
     * @param request 权限信息
     * @return 更新后的权限
     */
    PermissionVO updatePermission(Long permissionId, PermissionRequest request);

    /**
     * 删除未被角色使用的权限。
     * @param permissionId 权限 ID
     */
    void deletePermission(Long permissionId);
}
