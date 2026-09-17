package com.leetmodel.common.security.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * Token 会话生命周期工具类。
 *
 * <p>封装基于 Sa-Token 的登录签发、主动登出、强行踢人与登录态探测；
 * 登出与踢人时自动将 Token 压入 Redis 黑名单以实现无状态 JWT 的主动失效。</p>
 */
public final class TokenUtil {

    private TokenUtil() {
        // 工具类禁止实例化
    }

    /**
     * 执行用户登录，签发并返回无状态 JWT Token。
     *
     * @param userId 登录用户 ID
     * @return 签发的 JWT Token 字符串
     */
    public static String login(Long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    /**
     * 执行当前用户登出，将当前 Token 加入 Redis 黑名单使其立即失效。
     */
    public static void logout() {
        StpUtil.logout();
    }

    /**
     * 强制指定用户下线，将其签发的所有 Token 全部封禁入黑名单。
     *
     * @param userId 待强制下线的目标用户 ID
     */
    public static void kickout(Long userId) {
        StpUtil.kickout(userId);
    }

    /**
     * 探测当前请求上下文是否处于已登录状态。
     *
     * @return true 表示已登录且 Token 有效，false 表示未登录或已被加入黑名单
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }
}
