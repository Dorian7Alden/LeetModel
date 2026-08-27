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

    @Operation(summary = "获取角色列表")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return roleAdminFeignClient.listRoles();
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{roleId}")
    public Result<RoleVO> detail(@PathVariable Long roleId) {
        return roleAdminFeignClient.getRole(roleId);
    }

    @Operation(summary = "创建角色")
    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody RoleRequest request) {
        return roleAdminFeignClient.createRole(request);
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{roleId}")
    public Result<RoleVO> update(@PathVariable Long roleId,
                                 @Valid @RequestBody RoleRequest request) {
        return roleAdminFeignClient.updateRole(roleId, request);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    public Result<Void> delete(@PathVariable Long roleId) {
        return roleAdminFeignClient.deleteRole(roleId);
    }

    @Operation(summary = "获取角色权限")
    @GetMapping("/{roleId}/permissions")
    public Result<List<PermissionVO>> getPermissions(@PathVariable Long roleId) {
        return roleAdminFeignClient.getRolePermissions(roleId);
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/{roleId}/permissions")
    public Result<Void> updatePermissions(@PathVariable Long roleId,
                                          @Valid @RequestBody RolePermissionsRequest request) {
        return roleAdminFeignClient.updateRolePermissions(roleId, request);
    }
}
