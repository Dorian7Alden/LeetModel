package com.leetmodel.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.dto.RoleRequest;
import com.leetmodel.user.service.RoleService;
import com.leetmodel.user.vo.RoleVO;
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
 * 管理员角色管理接口。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "管理员-角色管理")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "获取角色列表")
    public Result<List<RoleVO>> list() {
        List<RoleVO> roles = roleService.listRoles();
        return Result.ok(roles);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情")
    public Result<RoleVO> detail(@PathVariable Long id) {
        RoleVO vo = roleService.getRoleById(id);
        return Result.ok(vo);
    }

    @PostMapping
    @Operation(summary = "创建角色")
    public Result<RoleVO> create(@Valid @RequestBody RoleRequest request) {
        RoleVO vo = roleService.createRole(request);
        return Result.ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        RoleVO vo = roleService.updateRole(id, request);
        return Result.ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.ok();
    }
}
