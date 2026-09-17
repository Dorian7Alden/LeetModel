package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.feign.RoleAdminFeignClient;
import com.leetmodel.common.api.vo.PermissionVO;
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
 * 管理端权限管理接口。
 *
 * <p>面向客户端管理端页面，通过 RoleAdminFeignClient 调用 user-service
 * 完成权限 CRUD。本服务不操作数据库。</p>
 */
@Tag(name = "管理端-权限管理")
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminPermissionController {

    private final RoleAdminFeignClient roleAdminFeignClient;

    /**
     * 管理员查询系统所有细粒度权限列表。
     *
     * @return 权限视图对象列表
     */
    @Operation(summary = "获取权限列表")
    @GetMapping
    public Result<List<PermissionVO>> list() {
        return roleAdminFeignClient.listPermissions();
    }

    /**
     * 查看指定权限的详情定义。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 权限视图对象
     */
    @Operation(summary = "获取权限详情")
    @GetMapping("/{permissionId}")
    public Result<PermissionVO> detail(@PathVariable Long permissionId) {
        return roleAdminFeignClient.getPermission(permissionId);
    }

    /**
     * 录入新增的系统权限。
     *
     * @param request 包含编码、名称与描述的权限请求对象，不能为 null
     * @return 创建后的权限视图对象
     */
    @Operation(summary = "创建权限")
    @PostMapping
    public Result<PermissionVO> create(@Valid @RequestBody PermissionRequest request) {
        return roleAdminFeignClient.createPermission(request);
    }

    /**
     * 更新已有权限的信息定义。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @param request      包含修改内容的请求对象，不能为 null
     * @return 更新后的权限视图对象
     */
    @Operation(summary = "更新权限")
    @PutMapping("/{permissionId}")
    public Result<PermissionVO> update(@PathVariable Long permissionId,
                                       @Valid @RequestBody PermissionRequest request) {
        return roleAdminFeignClient.updatePermission(permissionId, request);
    }

    /**
     * 删除未被角色使用的废弃权限。
     *
     * @param permissionId 目标权限 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除权限")
    @DeleteMapping("/{permissionId}")
    public Result<Void> delete(@PathVariable Long permissionId) {
        return roleAdminFeignClient.deletePermission(permissionId);
    }
}
