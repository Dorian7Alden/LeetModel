package com.leetmodel.common.cache.config;

/**
 * 统一生成业务 Redis Key 和频道名。
 */
public final class CacheNamespace {

    private final String environment;
    private final String ownerService;

    /**
     * 创建缓存命名空间。
     *
     * @param environment 环境名
     * @param ownerService 数据所有者服务名
     */
    public CacheNamespace(String environment, String ownerService) {
        this.environment = normalize(environment, "environment");
        this.ownerService = normalize(ownerService, "ownerService");
    }

    /**
     * 返回服务名。
     *
     * @return 服务名
     */
    public String ownerService() {
        return ownerService;
    }

    /**
     * 返回缓存代际 Key。
     *
     * @return 代际 Key
     */
    public String generationKey() {
        return "lm:" + environment + ":cache:meta:generation";
    }

    /**
     * 返回失效广播频道。
     *
     * @return 频道名
     */
    public String invalidationChannel() {
        return "lm:" + environment + ":cache:invalidation";
    }

    /**
     * 返回区域版本 Key。
     *
     * @param generation 缓存代际
     * @param region 缓存区域
     * @param scopeKey 作用域
     * @return 版本 Key
     */
    public String revisionKey(String generation, String region, String scopeKey) {
        return prefix(generation, region, scopeKey) + ":revision";
    }

    /**
     * 返回缓存数据 Key。
     *
     * @param generation 缓存代际
     * @param region 缓存区域
     * @param scopeKey 作用域
     * @param schemaVersion 结构版本
     * @param revision 区域版本
     * @param logicalKey 逻辑 Key
     * @return 数据 Key
     */
    public String dataKey(
            String generation,
            String region,
            String scopeKey,
            String schemaVersion,
            long revision,
            String logicalKey
    ) {
        return prefix(generation, region, scopeKey)
                + ":" + normalize(schemaVersion, "schemaVersion")
                + ":r" + revision
                + ":" + logicalKey;
    }

    /**
     * 返回区域 Key 前缀。
     *
     * @param generation 缓存代际
     * @param region 区域
     * @param scopeKey 作用域
     * @return Key 前缀
     */
    private String prefix(String generation, String region, String scopeKey) {
        return "lm:" + environment + ":cache:g" + normalize(generation, "generation")
                + ":" + ownerService
                + ":" + normalize(region, "region")
                + ":" + normalize(scopeKey, "scopeKey");
    }

    /**
     * 标准化 Key 分段。
     *
     * @param value 原始值
     * @param name 字段名
     * @return 标准化值
     */
    private static String normalize(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException(name + " contains unsupported characters");
        }
        return normalized;
    }
}
