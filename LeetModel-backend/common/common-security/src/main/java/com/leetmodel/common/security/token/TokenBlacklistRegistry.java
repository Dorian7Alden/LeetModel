package com.leetmodel.common.security.token;

/**
 * Token 黑名单服务注册表。
 *
 * <p>Sa-Token 的生命周期工具类为静态方法，无法直接注入容器 Bean；容器启动时由
 * {@link TokenBlacklistService} 注册自身，工具类据此复用同一份黑名单逻辑。
 * 未注册（例如纯单元测试环境）时按未启用处理。</p>
 */
public final class TokenBlacklistRegistry {

    private static volatile TokenBlacklistService service;

    private TokenBlacklistRegistry() {
        // 工具类禁止实例化
    }

    /**
     * 注册黑名单服务实例。
     *
     * @param target 容器中的黑名单服务
     */
    public static void register(TokenBlacklistService target) {
        service = target;
    }

    /**
     * 获取黑名单服务实例。
     *
     * @return 已注册的服务实例；未注册时返回 null
     */
    public static TokenBlacklistService get() {
        return service;
    }
}
