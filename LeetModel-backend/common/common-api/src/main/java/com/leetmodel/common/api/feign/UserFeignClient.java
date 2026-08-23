package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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

    /**
     * 判断用户是否存在且可用。
     *
     * @param userId 用户 ID
     * @return 用户是否存在且可用
     */
    @GetMapping("/{userId}/available")
    Result<Boolean> isUserAvailable(@PathVariable("userId") Long userId);

    /**
     * 批量查询用户公开摘要。
     *
     * @param userIds 用户 ID 列表
     * @return 用户公开摘要列表
     */
    @GetMapping("/public-summaries")
    Result<List<UserPublicSummaryDTO>> getPublicSummaries(@RequestParam("userIds") List<Long> userIds);

    /**
     * 获取用户数量。
     *
     * @return 用户数量
     */
    @GetMapping("/count")
    Result<Long> getUserCount();
}
