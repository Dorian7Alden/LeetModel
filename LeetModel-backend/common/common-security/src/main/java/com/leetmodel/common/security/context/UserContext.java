package com.leetmodel.common.security.context;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 用户身份上下文工具类。
 *
 * <p>从当前请求绑定的 Sa-Token 登录态中提取用户 ID、Token 及角色信息，
 * 供业务层查询当前操作人身份及 Feign 跨服务透传 Token 使用。</p>
 */
public final class UserContext {

    private UserContext() {
        // 工具类禁止实例化
    }

    /**
     * 获取当前登录用户的唯一 ID。
     *
     * @return 用户 ID，未登录时抛出 NotLoginException
     * @throws cn.dev33.satoken.exception.NotLoginException 当请求上下文未携带合法登录凭证时抛出
     */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前请求的 Token 字符串。
     *
     * @return 当前请求携带的有效 JWT Token 字符串；未登录时返回 null
     */
    public static String getToken() {
        return StpUtil.getTokenValue();
    }

    /**
     * 判断当前用户是否拥有指定角色。
     *
     * @param role 角色编码，如 "vip"
     * @return true 表示当前登录用户具备该角色，false 表示不具备
     */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }
}
