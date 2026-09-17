package com.leetmodel.user.controller;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.service.RoleService;
import com.leetmodel.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

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
    private final StorageService storageService;

    /**
     * 获取指定用户的角色编码与权限列表（供 common-security 鉴权使用）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 包含用户角色与权限编码的 DTO
     */
    @Operation(summary = "获取用户角色数据")
    @GetMapping("/{userId}/roles")
    public Result<UserRoleDTO> getUserRoles(@PathVariable Long userId) {
        UserRoleDTO dto = roleService.getUserRoles(userId);
        return Result.ok(dto);
    }

    /**
     * 检查指定用户是否存在且处于正常可用状态（供 team-service 校验成员）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 若用户存在且状态为正常返回 true，否则返回 false
     */
    @Operation(summary = "判断用户是否可加入团队")
    @GetMapping("/{userId}/available")
    public Result<Boolean> isUserAvailable(@PathVariable Long userId) {
        User user = userService.getById(userId);
        boolean available = user != null && user.getStatus() == 1;
        return Result.ok(available);
    }

    /**
     * 批量查询指定用户列表的公开名片摘要信息。
     *
     * @param userIds 目标用户 ID 列表，可为空
     * @return 匹配的用户公开摘要列表
     */
    @Operation(summary = "批量获取用户公开摘要")
    @GetMapping("/public-summaries")
    public Result<List<UserPublicSummaryDTO>> getPublicSummaries(
            @RequestParam(required = false) List<Long> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Result.ok(List.of());
        }
        List<User> users = userService.listByIds(userIds);
        List<UserPublicSummaryDTO> summaries = new ArrayList<>();
        for (User user : users) {
            summaries.add(new UserPublicSummaryDTO(
                    user.getId(),
                    user.getNickname(),
                    resolveAvatarUrl(user.getAvatarPath())
            ));
        }
        return Result.ok(summaries);
    }

    /**
     * 统计系统全量注册用户总数（供 admin-service 看板聚合）。
     *
     * @return 系统当前用户总数
     */
    @Operation(summary = "获取用户数量")
    @GetMapping("/count")
    public Result<Long> getUserCount() {
        long count = userService.count();
        return Result.ok(count);
    }

    /**
     * 将头像对象路径转换为访问地址。
     *
     * @param avatarPath 头像对象路径
     * @return 头像访问地址
     */
    private String resolveAvatarUrl(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) return null;
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) return avatarPath;
        return storageService.getUrl(avatarPath);
    }
}
