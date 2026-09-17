package com.leetmodel.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.common.api.dto.UserRolesRequest;
import com.leetmodel.common.api.dto.UserStatusRequest;
import com.leetmodel.common.api.vo.UserAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.service.UserService;
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
 * 内部用户管理接口。
 *
 * <p>仅供 admin-service 通过 Feign 调用，不暴露给客户端。路径匹配
 * common-api 中的 UserAdminFeignClient。用户数据的真实读写都在这里完成，
 * admin-service 只做管理端门面转发。</p>
 */
@Tag(name = "内部接口-用户管理")
@RestController
@RequestMapping("/internal/admin/users")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class InternalAdminUserController {

    private final UserService userService;

    /**
     * 管理员分页组合条件查询平台用户列表。
     *
     * @param query 包含关键词、状态、分页等过滤条件的查询对象
     * @return 分页包装的用户管理端 VO 列表
     */
    @Operation(summary = "分页查询用户列表")
    @GetMapping
    public Result<PageResult<UserAdminVO>> pageUsers(UserPageQuery query) {
        IPage<UserAdminVO> page = userService.listUsers(query);
        return Result.ok(PageResult.from(page));
    }

    /**
     * 管理员查询指定用户的详细信息（含角色列表）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 用户管理端明细 VO
     */
    @Operation(summary = "查看用户详情")
    @GetMapping("/{userId}")
    public Result<UserAdminVO> getUserDetail(@PathVariable Long userId) {
        return Result.ok(userService.getUserDetail(userId));
    }

    /**
     * 管理员启用或禁用指定用户的账号状态。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含目标状态的请求对象，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "启用或禁用用户")
    @PutMapping("/{userId}/status")
    public Result<Void> updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UserStatusRequest request
    ) {
        userService.updateStatus(userId, request.getStatus());
        return Result.ok();
    }

    /**
     * 管理员重置或更新指定用户分配的角色集合。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含角色 ID 列表的请求对象，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "更新用户角色")
    @PutMapping("/{userId}/roles")
    public Result<Void> updateUserRoles(
            @PathVariable Long userId,
            @Valid @RequestBody UserRolesRequest request
    ) {
        userService.updateRoles(userId, request.getRoleIds());
        return Result.ok();
    }
}
