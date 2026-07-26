package com.leetmodel.common.security.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * Token 操作工具类 —— 封装 Sa-Token 的登录/登出/踢人操作。
 *
 * <p>业务层通过此工具操作 Token，而非直接调用 StpUtil，
 * 方便统一管理 Token 生命周期，添加日志或埋点。</p>
 *
 * @author LeetModel
 */
public final class TokenUtil {

    private TokenUtil() {
        // 工具类禁止实例化
    }

    /**
     * 登录，生成并返回 JWT Token。
     *
     * @param userId 登录用户 ID
     * @return JWT Token 字符串
     */
    public static String login(Long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    /**
     * 登出：将当前 Token 加入 Redis 黑名单。
     * 即使 JWT 未过期，加入黑名单的 Token 也会被拒绝。
     */
    public static void logout() {
        StpUtil.logout();
    }

    /**
     * 踢人下线：将指定用户的所有 Token 加入黑名单。
     *
     * @param userId 被踢用户 ID
     */
    public static void kickout(Long userId) {
        StpUtil.kickout(userId);
    }

    /**
     * 判断当前请求是否已登录。
     *
     * @return true = 已登录
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }
}
