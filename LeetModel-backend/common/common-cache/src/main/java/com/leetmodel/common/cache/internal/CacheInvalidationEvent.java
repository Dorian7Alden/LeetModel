package com.leetmodel.common.cache.internal;

import java.time.LocalDateTime;

/**
 * 可幂等投递的缓存失效事件。
 *
 * @param eventId 事件唯一标识
 * @param ownerService 数据所有者服务
 * @param region 缓存区域
 * @param scopeKey 失效作用域
 * @param revision 数据库单调事件版本
 * @param schemaVersion 读模型版本
 * @param occurredAt 业务事务时间
 * @param generation 投递时缓存代际
 */
record CacheInvalidationEvent(
        String eventId,
        String ownerService,
        String region,
        String scopeKey,
        long revision,
        String schemaVersion,
        LocalDateTime occurredAt,
        String generation
) {

    /**
     * 返回区域作用域标识。
     *
     * @return 作用域标识
     */
    String scopeId() {
        return region + ":" + scopeKey;
    }

    /**
     * 为 Redis 投递创建携带代际的副本。
     *
     * @param cacheGeneration 缓存代际
     * @return 投递事件
     */
    CacheInvalidationEvent withGeneration(String cacheGeneration) {
        return new CacheInvalidationEvent(
                eventId,
                ownerService,
                region,
                scopeKey,
                revision,
                schemaVersion,
                occurredAt,
                cacheGeneration
        );
    }
}
