package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.api.feign.RoleAdminFeignClient;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端权限查看接口。
 *
 * <p>面向客户端管理端页面，通过 RoleAdminFeignClient 调用 user-service
 * 查询权限列表。本服务不操作数据库。</p>
 */
@Tag(name = "管理端-权限查看")
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminPermissionController {

    private final RoleAdminFeignClient roleAdminFeignClient;

    @Operation(summary = "获取权限列表")
    @GetMapping
    public Result<List<PermissionVO>> list() {
        return roleAdminFeignClient.listPermissions();
    }
}
