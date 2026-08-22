package com.leetmodel.user.controller;

import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.result.Result;
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
@RequiredArgsConstructor
public class InternalAdminRoleController {

    private final RoleService roleService;

    @Operation(summary = "获取角色列表")
    @GetMapping("/roles")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(roleService.listRoles());
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/roles/{roleId}")
    public Result<RoleVO> getRole(@PathVariable Long roleId) {
        return Result.ok(roleService.getRoleById(roleId));
    }

    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public Result<RoleVO> createRole(@Valid @RequestBody RoleRequest request) {
        return Result.ok(roleService.createRole(request));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/roles/{roleId}")
    public Result<RoleVO> updateRole(@PathVariable Long roleId,
                                     @Valid @RequestBody RoleRequest request) {
        return Result.ok(roleService.updateRole(roleId, request));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{roleId}")
    public Result<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return Result.ok();
    }

    @Operation(summary = "获取权限列表")
    @GetMapping("/permissions")
    public Result<List<PermissionVO>> listPermissions() {
        return Result.ok(roleService.listPermissions());
    }
}
