package com.leetmodel.common.cache;

/**
 * 为 HTTP 条件缓存提供当前区域版本。
 */
public interface CacheVersionProvider {

    /**
     * 读取当前缓存版本。Redis 不可用时返回五秒紧急时间桶。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     * @return 缓存版本
     */
    CacheVersionView current(String region, String scopeKey);
}
