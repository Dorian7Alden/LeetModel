package com.leetmodel.user.controller;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.service.RoleService;
import com.leetmodel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部用户查询接口。
 *
 * <p>仅供微服务间通过 Feign 调用，不暴露给客户端。路径匹配 common-api
 * 中的 UserFeignClient。为 common-security 提供鉴权角色权限数据，
 * 为 admin-service 提供用户统计数量。</p>
 */
@Tag(name = "内部接口-用户查询")
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final RoleService roleService;
    private final UserService userService;

    @Operation(summary = "获取用户角色数据")
    @GetMapping("/{userId}/roles")
    public Result<UserRoleDTO> getUserRoles(@PathVariable Long userId) {
        UserRoleDTO dto = roleService.getUserRoles(userId);
        return Result.ok(dto);
    }

    @Operation(summary = "获取用户数量")
    @GetMapping("/count")
    public Result<Long> getUserCount() {
        long count = userService.count();
        return Result.ok(count);
    }
}
