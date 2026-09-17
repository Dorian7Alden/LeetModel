package com.leetmodel.common.cache;

import java.time.Duration;
import java.util.Objects;

/**
 * 定义一次强类型多级缓存读取的命名与时间边界。
 *
 * @param region 缓存区域
 * @param scopeKey 失效作用域
 * @param schemaVersion 读模型结构版本
 * @param logicalKey 标准化逻辑 Key
 * @param localTtl Caffeine 正值 TTL
 * @param redisTtl Redis 正值 TTL
 * @param localNegativeTtl Caffeine 空值 TTL
 * @param redisNegativeTtl Redis 空值 TTL
 */
public record CacheSpec(
        String region,
        String scopeKey,
        String schemaVersion,
        String logicalKey,
        Duration localTtl,
        Duration redisTtl,
        Duration localNegativeTtl,
        Duration redisNegativeTtl
) {

    /**
     * 校验缓存契约。
     */
    public CacheSpec {
        requireSegment(region, "region");
        requireSegment(scopeKey, "scopeKey");
        requireSegment(schemaVersion, "schemaVersion");
        requireLogicalKey(logicalKey);
        requirePositive(localTtl, "localTtl");
        requirePositive(redisTtl, "redisTtl");
        requirePositive(localNegativeTtl, "localNegativeTtl");
        requirePositive(redisNegativeTtl, "redisNegativeTtl");
        if (localTtl.compareTo(redisTtl) > 0) {
            throw new IllegalArgumentException("localTtl must not exceed redisTtl");
        }
    }

    /**
     * 返回区域作用域标识。
     *
     * @return 区域作用域标识
     */
    public String scopeId() {
        return region + ":" + scopeKey;
    }

    /**
     * 校验字符串不为空。
     *
     * @param value 字符串
     * @param name 字段名
     */
    private static void requireSegment(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        if (value.contains(":")) throw new IllegalArgumentException(name + " must not contain ':'");
    }

    /**
     * 校验逻辑 Key 只包含可观测且不会引入空白的安全字符。
     *
     * @param value 逻辑 Key
     */
    private static void requireLogicalKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("logicalKey must not be blank");
        }
        if (!value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("logicalKey contains unsupported characters");
        }
    }

    /**
     * 校验时长为正数。
     *
     * @param duration 时长
     * @param name 字段名
     */
    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
