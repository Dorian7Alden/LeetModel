package com.leetmodel.common.core.security;

/**
 * Token 黑名单的键与有效期规则。
 *
 * <p>规则与 Redis 客户端无关，网关的响应式实现和业务服务的阻塞式实现共用同一套口径，
 * 避免两侧算出不同的键或 TTL。</p>
 */
public final class TokenBlacklistPolicy {

    /** Token 已过期或无法解析剩余有效期时使用的兜底 TTL，保证记录仍会在有限时间内清理。 */
    private static final long FALLBACK_TTL_SECONDS = 1L;

    private TokenBlacklistPolicy() {
        // 工具类禁止实例化
    }

    /**
     * 生成黑名单键。
     *
     * @param keyPrefix 配置的键前缀
     * @param token     Token 原文
     * @return 黑名单键
     */
    public static String key(String keyPrefix, String token) {
        return keyPrefix + TokenFingerprint.of(token);
    }

    /**
     * 计算黑名单记录的 TTL。
     *
     * <p>取 Token 剩余有效期与配置上限中的较小值；剩余有效期不可用时使用兜底值，
     * 既不让记录永久驻留，也不会因为无法解析而漏写。</p>
     *
     * @param remainingSeconds Token 剩余有效秒数，负数表示无法解析
     * @param maxTtlSeconds    配置的 TTL 上限
     * @return 实际写入 Redis 的 TTL 秒数
     */
    public static long ttlSeconds(long remainingSeconds, long maxTtlSeconds) {
        long limit = maxTtlSeconds > 0 ? maxTtlSeconds : FALLBACK_TTL_SECONDS;
        if (remainingSeconds <= 0) {
            return Math.min(FALLBACK_TTL_SECONDS, limit);
        }
        return Math.min(remainingSeconds, limit);
    }
}
