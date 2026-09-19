package com.leetmodel.common.security.token;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.common.core.security.TokenBlacklistPolicy;
import com.leetmodel.common.core.security.TokenBlacklistProperties;
import com.leetmodel.common.core.security.TokenFingerprint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token 黑名单读写。
 *
 * <p>登出后按 Token 指纹写入安全状态 Redis 并使后续请求失效；Redis 不可用时按配置
 * 决定放行或拒绝，两种情况都会累加降级计数，不阻塞业务主链的其它环节。</p>
 */
@Slf4j
@Service
public class TokenBlacklistService {

    /** 黑名单命中时抛出的 NotLoginException 类型，供统一异常处理区分提示语。 */
    public static final String BLACKLISTED_TOKEN_TYPE = "BLACKLISTED_TOKEN";

    private static final String REVOKED_VALUE = "1";

    private final StringRedisTemplate redisTemplate;
    private final TokenBlacklistProperties properties;
    private final Counter revokedCounter;
    private final Counter hitCounter;
    private final Counter degradedCounter;

    public TokenBlacklistService(StringRedisTemplate redisTemplate,
                                 TokenBlacklistProperties properties,
                                 ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable(() -> Metrics.globalRegistry);
        this.revokedCounter = meterRegistry.counter("auth_token_blacklist_revoked_total");
        this.hitCounter = meterRegistry.counter("auth_token_blacklist_hit_total");
        this.degradedCounter = meterRegistry.counter("auth_token_blacklist_degraded_total");
        // 静态工具类 TokenUtil 通过注册表访问本实例
        TokenBlacklistRegistry.register(this);
    }

    /**
     * 将 Token 写入黑名单。
     *
     * @param token            Token 原文，为空时直接忽略
     * @param remainingSeconds Token 剩余有效秒数，无法解析时传负数
     */
    public void revoke(String token, long remainingSeconds) {
        if (!properties.isEnabled() || token == null || token.isBlank()) {
            return;
        }
        long ttlSeconds = TokenBlacklistPolicy.ttlSeconds(remainingSeconds, properties.getMaxTtlSeconds());
        String key = TokenBlacklistPolicy.key(properties.getKeyPrefix(), token);
        String fingerprintPrefix = TokenFingerprint.logPrefix(TokenFingerprint.of(token));
        try {
            redisTemplate.opsForValue().set(key, REVOKED_VALUE, Duration.ofSeconds(ttlSeconds));
            revokedCounter.increment();
            log.info("Token 已加入黑名单: fingerprint={}, ttlSeconds={}", fingerprintPrefix, ttlSeconds);
        } catch (RuntimeException exception) {
            degradedCounter.increment();
            log.warn("Token 黑名单写入失败，登出仅保留本地语义: fingerprint={}, exceptionType={}",
                    fingerprintPrefix, exception.getClass().getSimpleName());
        }
    }

    /**
     * 判断 Token 是否已被拉黑。

     * @param token Token 原文，为空时视为未拉黑
     * @return true 表示命中黑名单
     */
    public boolean isRevoked(String token) {
        if (!properties.isEnabled() || token == null || token.isBlank()) {
            return false;
        }
        String key = TokenBlacklistPolicy.key(properties.getKeyPrefix(), token);
        try {
            Boolean exists = redisTemplate.hasKey(key);
            boolean revoked = Boolean.TRUE.equals(exists);
            if (revoked) {
                hitCounter.increment();
            }
            return revoked;
        } catch (RuntimeException exception) {
            degradedCounter.increment();
            log.warn("Token 黑名单查询降级: failClosed={}, exceptionType={}",
                    properties.isFailClosed(), exception.getClass().getSimpleName());
            return properties.isFailClosed();
        }
    }

    /**
     * 校验 Token，命中黑名单时抛出未登录异常。
     *
     * @param token Token 原文，为空时不校验
     */
    public void rejectIfRevoked(String token) {
        if (!isRevoked(token)) {
            return;
        }
        throw new NotLoginException("登录已失效，请重新登录", StpUtil.getLoginType(),
                BLACKLISTED_TOKEN_TYPE);
    }
}
