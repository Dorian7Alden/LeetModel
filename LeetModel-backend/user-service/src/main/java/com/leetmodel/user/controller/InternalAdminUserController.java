package com.leetmodel.user.controller;

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
@RequiredArgsConstructor
public class InternalAdminUserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户列表")
    @GetMapping
    public Result<PageResult<UserAdminVO>> pageUsers(UserPageQuery query) {
        IPage<UserAdminVO> page = userService.listUsers(query);
        return Result.ok(PageResult.from(page));
    }

    @Operation(summary = "查看用户详情")
    @GetMapping("/{userId}")
    public Result<UserAdminVO> getUserDetail(@PathVariable Long userId) {
        return Result.ok(userService.getUserDetail(userId));
    }

    @Operation(summary = "启用或禁用用户")
    @PutMapping("/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable Long userId,
                                         @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(userId, request.getStatus());
        return Result.ok();
    }

    @Operation(summary = "更新用户角色")
    @PutMapping("/{userId}/roles")
    public Result<Void> updateUserRoles(@PathVariable Long userId,
                                        @Valid @RequestBody UserRolesRequest request) {
        userService.updateRoles(userId, request.getRoleIds());
        return Result.ok();
    }
}
