package com.leetmodel.user.controller;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部 Feign 接口实现 —— 为 common-security 的 {@code StpInterfaceImpl} 提供角色权限数据。
 * 路径匹配 common-api 的 {@code UserFeignClient} 声明。
 *
 * @author LeetModel
 */
@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final RoleService roleService;

    @GetMapping("/{userId}/roles")
    public Result<UserRoleDTO> getUserRoles(@PathVariable Long userId) {
        UserRoleDTO dto = roleService.getUserRoles(userId);
        return Result.ok(dto);
    }
}
