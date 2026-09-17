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
 * <p>触发 @SaCheckRole 或 @SaCheckPermission 时回调此类，通过 UserFeignClient 向 user-service
 * 查询用户的角色和权限编码；在服务故障或降级时返回空列表并由处理器映射为 403（保证安全不倒置）。</p>
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    /** 用户微服务 Feign 远程客户端 */
    private final UserFeignClient userFeignClient;

    /**
     * 获取当前登录用户的权限编码列表。
     *
     * @param loginId   登录账号主键 ID
     * @param loginType 账号登录体系类型
     * @return 权限编码集合；查询失败或无权限时返回空列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return fetchRoles(loginId).getPermissions();
    }

    /**
     * 获取当前登录用户的角色编码列表。
     *
     * @param loginId   登录账号主键 ID
     * @param loginType 账号登录体系类型
     * @return 角色编码集合；查询失败或无角色时返回空列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return fetchRoles(loginId).getRoles();
    }

    /**
     * 通过 Feign 远程查询用户服务的 RBAC 角色与权限数据。
     *
     * @param loginId 登录账号主键 ID
     * @return 包含角色与权限列表的 UserRoleDTO 传输对象；降级时返回包含空列表的对象
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
