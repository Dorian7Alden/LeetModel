package com.leetmodel.common.security.handler;

import cn.dev33.satoken.stp.StpInterface;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色数据源实现。
 *
 * <p>每次鉴权注解（@SaCheckRole / @SaCheckPermission）触发时，
 * Sa-Token 调用此实现获取当前用户的角色和权限列表进行比对。</p>
 *
 * <p>通过 Feign 调用 user 服务查询 RBAC 数据，
 * user 服务不可用时走 Feign 降级。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserFeignClient userFeignClient;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return fetchRoles(loginId).getPermissions();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return fetchRoles(loginId).getRoles();
    }

    /**
     * 通过 Feign 调用 user 服务获取角色和权限。
     * 失败时返回空列表，AuthExceptionHandler 会将其转换为 403 响应。
     */
    private UserRoleDTO fetchRoles(Object loginId) {
        try {
            Result<UserRoleDTO> result = userFeignClient.getUserRoles(Long.valueOf(loginId.toString()));
            if (result.isSuccess() && result.getData() != null) {
                return result.getData();
            }
        } catch (Exception e) {
            // Feign 调用失败时走降级——Spring 会使用 UserFeignFallback 的结果
        }
        UserRoleDTO fallback = new UserRoleDTO();
        fallback.setRoles(Collections.emptyList());
        fallback.setPermissions(Collections.emptyList());
        return fallback;
    }
}
