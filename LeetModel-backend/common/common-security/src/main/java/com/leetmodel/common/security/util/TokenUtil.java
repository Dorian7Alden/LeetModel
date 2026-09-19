package com.leetmodel.common.security.util;

import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.common.security.token.TokenBlacklistRegistry;
import com.leetmodel.common.security.token.TokenBlacklistService;

/**
 * Token 会话生命周期工具类。
 *
 * <p>封装基于 Sa-Token 的登录签发、主动登出与登录态探测。JWT 无状态模式下 Sa-Token
 * 自身不提供黑名单，登出时由 {@link TokenBlacklistService} 按 Token 指纹写入安全状态 Redis，
 * 使 Token 在网关与业务服务两侧立即失效。</p>
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
     * 执行当前用户登出：先把当前 Token 写入 Redis 黑名单使其立即失效，再清理本地登录态。
     */
    public static void logout() {
        String token = StpUtil.getTokenValue();
        TokenBlacklistService blacklistService = TokenBlacklistRegistry.get();
        if (blacklistService != null && token != null && !token.isBlank()) {
            // JWT 无状态模式下该值为 Token 剩余有效秒数，无法解析时返回负值交由策略兜底
            long remainingSeconds = StpUtil.getTokenTimeout();
            blacklistService.revoke(token, remainingSeconds);
        }
        StpUtil.logout();
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
