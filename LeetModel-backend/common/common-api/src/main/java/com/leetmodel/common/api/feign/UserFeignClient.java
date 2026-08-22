package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * user 服务的内部 API（Feign 声明）。
 *
 * <p>由 common-security 的 {@code StpInterfaceImpl} 调用，
 * 获取当前用户的角色和权限列表，供 Sa-Token 注解鉴权使用。</p>
 */
@FeignClient(
        name = "user-service",
        contextId = "userFeignClient",
        path = "/internal/users",
        fallbackFactory = UserFeignFallback.class
)
public interface UserFeignClient {

    /**
     * 根据用户 ID 查询角色和权限列表。
     *
     * @param userId 用户 ID
     * @return 角色和权限信息
     */
    @GetMapping("/{userId}/roles")
    Result<UserRoleDTO> getUserRoles(@PathVariable("userId") Long userId);

    @GetMapping("/count")
    Result<Long> getUserCount();
}
