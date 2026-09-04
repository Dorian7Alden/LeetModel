package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.common.api.dto.UserRolesRequest;
import com.leetmodel.common.api.dto.UserStatusRequest;
import com.leetmodel.common.api.feign.UserAdminFeignClient;
import com.leetmodel.common.api.vo.UserAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理接口。
 *
 * <p>面向客户端管理端页面，通过 UserAdminFeignClient 调用 user-service
 * 完成用户分页、详情、状态变更和角色分配。本服务不操作数据库。</p>
 */
@Tag(name = "管理端-用户管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminFeignClient userAdminFeignClient;

    /**
     * 管理员分页组合条件查询用户列表。
     *
     * @param query 分页与关键词过滤参数对象，不能为 null
     * @return 分页包装的用户管理端明细 VO
     */
    @Operation(summary = "分页查询用户列表")
    @SaCheckPermission("user:read")
    @GetMapping
    public Result<PageResult<UserAdminVO>> list(@Valid UserPageQuery query) {
        return userAdminFeignClient.page(query);
    }

    /**
     * 查看指定用户的详细资料与角色分配。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 用户管理端明细 VO
     */
    @Operation(summary = "查看用户详情")
    @SaCheckPermission("user:read")
    @GetMapping("/{userId}")
    public Result<UserAdminVO> detail(@PathVariable Long userId) {
        return userAdminFeignClient.detail(userId);
    }

    /**
     * 管理员启用或禁用指定用户的登录账号。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含新状态的请求对象，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "启用或禁用用户")
    @SaCheckPermission("user:update")
    @PutMapping("/{userId}/status")
    public Result<Void> updateStatus(@PathVariable Long userId,
                                     @Valid @RequestBody UserStatusRequest request) {
        return userAdminFeignClient.updateStatus(userId, request);
    }

    /**
     * 管理员全量更新指定用户的角色分配。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含角色 ID 列表的请求对象，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "更新用户角色")
    @SaCheckPermission("user:update")
    @PutMapping("/{userId}/roles")
    public Result<Void> updateRoles(@PathVariable Long userId,
                                    @Valid @RequestBody UserRolesRequest request) {
        return userAdminFeignClient.updateRoles(userId, request);
    }
}
