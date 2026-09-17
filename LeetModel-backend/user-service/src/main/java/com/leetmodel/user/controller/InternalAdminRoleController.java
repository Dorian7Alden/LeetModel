package com.leetmodel.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.dto.RolePermissionsRequest;
import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.service.PermissionService;
import com.leetmodel.user.service.RoleService;
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
 * 内部角色权限管理接口。
 *
 * <p>仅供 admin-service 通过 Feign 调用，不暴露给客户端。路径匹配
 * common-api 中的 RoleAdminFeignClient。角色 CRUD 与权限查询的真实
 * 数据操作都在这里完成，admin-service 只做管理端门面转发。</p>
 */
@Tag(name = "内部接口-角色权限管理")
@RestController
@RequestMapping("/internal/admin")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class InternalAdminRoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    /**
     * 查询系统中全部预设与自定义角色列表。
     *
     * @return 角色视图对象列表
     */
    @Operation(summary = "获取角色列表")
    @GetMapping("/roles")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(roleService.listRoles());
    }

    /**
     * 查询指定角色的基本信息详情。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 角色视图对象
     */
    @Operation(summary = "获取角色详情")
    @GetMapping("/roles/{roleId}")
    public Result<RoleVO> getRole(@PathVariable Long roleId) {
        return Result.ok(roleService.getRoleById(roleId));
    }

    /**
     * 创建新的业务角色。
     *
     * @param request 包含角色编码、名称与描述的请求对象，不能为 null
     * @return 创建成功后的角色视图对象
     */
    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public Result<RoleVO> createRole(@Valid @RequestBody RoleRequest request) {
        return Result.ok(roleService.createRole(request));
    }

    /**
     * 修改指定角色的基本信息。
     *
     * @param roleId  目标角色 ID，不能为 null
     * @param request 包含待修改角色属性的请求对象，不能为 null
     * @return 更新后的角色视图对象
     */
    @Operation(summary = "更新角色")
    @PutMapping("/roles/{roleId}")
    public Result<RoleVO> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody RoleRequest request
    ) {
        return Result.ok(roleService.updateRole(roleId, request));
    }

    /**
     * 删除指定非预设角色，并级联清理用户角色与角色权限关系。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{roleId}")
    public Result<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return Result.ok();
    }

    /**
     * 查询指定角色拥有的全部权限列表。
     *
     * @param roleId 目标角色 ID，不能为 null
     * @return 权限视图对象列表
     */
    @Operation(summary = "获取角色权限")
    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<PermissionVO>> getRolePermissions(@PathVariable Long roleId) {
        return Result.ok(roleService.getRolePermissions(roleId));
    }

    /**
     * 全量更新指定角色所绑定的权限集合。
     *
     * @param roleId  目标角色 ID，不能为 null
     * @param request 包含新权限 ID 列表的请求对象，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "更新角色权限")
    @PutMapping("/roles/{roleId}/permissions")
    public Result<Void> updateRolePermissions(
            @PathVariable Long roleId,
            @Valid @RequestBody RolePermissionsRequest request
    ) {
        roleService.updateRolePermissions(roleId, request.getPermissionIds());
        return Result.ok();
    }

    /**
     * 查询系统已注册的全部细粒度权限列表。
     *
     * @return 权限视图对象列表
     */
    @Operation(summary = "获取权限列表")
    @GetMapping("/permissions")
    public Result<List<PermissionVO>> listPermissions() {
        return Result.ok(permissionService.listPermissions());
    }

    /**
     * 查询指定权限的详情定义。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 权限视图对象
     */
    @Operation(summary = "获取权限详情")
    @GetMapping("/permissions/{permissionId}")
    public Result<PermissionVO> getPermission(@PathVariable Long permissionId) {
        return Result.ok(permissionService.getPermissionById(permissionId));
    }

    /**
     * 录入新增的系统权限定义。
     *
     * @param request 包含权限编码、名称与描述的请求对象，不能为 null
     * @return 创建成功后的权限视图对象
     */
    @Operation(summary = "创建权限")
    @PostMapping("/permissions")
    public Result<PermissionVO> createPermission(@Valid @RequestBody PermissionRequest request) {
        return Result.ok(permissionService.createPermission(request));
    }

    /**
     * 修改已有权限的编码、名称或说明。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @param request      包含待修改权限属性的请求对象，不能为 null
     * @return 更新后的权限视图对象
     */
    @Operation(summary = "更新权限")
    @PutMapping("/permissions/{permissionId}")
    public Result<PermissionVO> updatePermission(
            @PathVariable Long permissionId,
            @Valid @RequestBody PermissionRequest request
    ) {
        return Result.ok(permissionService.updatePermission(permissionId, request));
    }

    /**
     * 删除未被任何角色引用的废弃权限定义。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除权限")
    @DeleteMapping("/permissions/{permissionId}")
    public Result<Void> deletePermission(@PathVariable Long permissionId) {
        permissionService.deletePermission(permissionId);
        return Result.ok();
    }
}
