package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.api.dto.RolePermissionsRequest;
import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.feign.RoleAdminFeignClient;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端角色管理接口。
 *
 * <p>面向客户端管理端页面，通过 RoleAdminFeignClient 调用 user-service
 * 完成角色 CRUD。本服务不操作数据库。</p>
 */
@Tag(name = "管理端-角色管理")
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminRoleController {

    private final RoleAdminFeignClient roleAdminFeignClient;

    /**
     * 管理员查询系统所有角色列表。
     *
     * @return 角色视图对象列表
     */
    @Operation(summary = "获取角色列表")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return roleAdminFeignClient.listRoles();
    }

    /**
     * 查看指定角色的基础信息。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 角色视图对象
     */
    @Operation(summary = "获取角色详情")
    @GetMapping("/{roleId}")
    public Result<RoleVO> detail(@PathVariable Long roleId) {
        return roleAdminFeignClient.getRole(roleId);
    }

    /**
     * 创建新的系统角色。
     *
     * @param request 包含编码、名称与描述的角色请求对象，不能为 null
     * @return 创建后的角色视图对象
     */
    @Operation(summary = "创建角色")
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody RoleRequest request) {
        return roleAdminFeignClient.createRole(request);
    }

    /**
     * 修改已有角色的基本信息。
     *
     * @param roleId  目标角色 ID，不能为 null
     * @param request 包含待修改属性的请求对象，不能为 null
     * @return 更新后的角色视图对象
     */
    @Operation(summary = "更新角色")
    @PutMapping("/{roleId}")
    public Result<RoleVO> update(@PathVariable Long roleId,
                                 @Valid @RequestBody RoleRequest request) {
        return roleAdminFeignClient.updateRole(roleId, request);
    }

    /**
     * 删除指定的非预设角色。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    public Result<Void> delete(@PathVariable Long roleId) {
        return roleAdminFeignClient.deleteRole(roleId);
    }

    /**
     * 查询指定角色拥有的权限列表。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 权限视图对象列表
     */
    @Operation(summary = "获取角色权限")
    @GetMapping("/{roleId}/permissions")
    public Result<List<PermissionVO>> getPermissions(@PathVariable Long roleId) {
        return roleAdminFeignClient.getRolePermissions(roleId);
    }

    /**
     * 全量更新指定角色所绑定的权限集合。
     *
     * @param roleId  目标角色 ID，不能为 null
     * @param request 包含权限 ID 列表的请求对象，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "更新角色权限")
    @PutMapping("/{roleId}/permissions")
    public Result<Void> updatePermissions(@PathVariable Long roleId,
                                          @Valid @RequestBody RolePermissionsRequest request) {
        return roleAdminFeignClient.updateRolePermissions(roleId, request);
    }
}
