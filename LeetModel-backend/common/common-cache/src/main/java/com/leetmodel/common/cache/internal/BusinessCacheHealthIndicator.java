package com.leetmodel.common.cache.internal;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 暴露业务 Redis 与 Outbox 降级状态。
 */
public final class BusinessCacheHealthIndicator implements HealthIndicator {

    private final BusinessRedisClient redisClient;
    private final CacheOutboxRepository repository;

    /**
     * 创建业务缓存健康检查。
     *
     * @param redisClient 业务 Redis 客户端
     * @param repository Outbox 仓库
     */
    public BusinessCacheHealthIndicator(
            BusinessRedisClient redisClient,
            CacheOutboxRepository repository
    ) {
        this.redisClient = redisClient;
        this.repository = repository;
    }

    /**
     * 返回业务缓存健康详情。Redis 不可用时业务仍可回源，因此使用 UNKNOWN 表示降级而不将服务标记为 DOWN。
     *
     * @return 健康状态
     */
    @Override
    public Health health() {
        try {
            boolean available = redisClient.ping();
            return Health.up()
                    .withDetail("redis", available ? "available" : "degraded")
                    .withDetail("pendingOutbox", repository.pendingCount())
                    .build();
        } catch (RuntimeException exception) {
            return Health.unknown()
                    .withDetail("redis", "degraded")
                    .withDetail("reason", exception.getClass().getSimpleName())
                    .build();
        }
    }
}
