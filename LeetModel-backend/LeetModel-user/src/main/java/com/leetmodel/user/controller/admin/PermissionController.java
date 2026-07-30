package com.leetmodel.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.service.RoleService;
import com.leetmodel.user.vo.PermissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员权限查看接口（只读 —— 权限由代码定义）。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "管理员-权限查看")
public class PermissionController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "获取权限列表")
    public Result<List<PermissionVO>> list() {
        List<PermissionVO> permissions = roleService.listPermissions();
        return Result.ok(permissions);
    }
}
