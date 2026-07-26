package com.leetmodel.common.security.context;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 用户上下文工具类 —— 从当前请求的 Token 中提取登录用户信息。
 *
 * <p>使用场景：
 * <ul>
 *   <li>Service 层需要知道"是谁在操作"，如 "只能查自己的提交记录"</li>
 *   <li>Feign 调用下游服务时，从上下文获取 Token 并注入 Header 透传</li>
 *   <li>Service 层根据角色做分支判断（不需要注解的场景）</li>
 * </ul>
 * </p>
 *
 * @author LeetModel
 */
public final class UserContext {

    private UserContext() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID，未登录时抛出 NotLoginException
     */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前请求的 Token 字符串。
     * 用于网关 → 业务服务或业务服务 → 下游服务的 Token 透传。
     *
     * @return JWT Token 字符串
     */
    public static String getToken() {
        return StpUtil.getTokenValue();
    }

    /**
     * 判断当前用户是否拥有指定角色。
     *
     * @param role 角色编码，如 "vip"
     * @return true = 拥有该角色
     */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }
}
