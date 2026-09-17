package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.common.cache.CacheSpec;
import com.leetmodel.common.cache.CacheVersionProvider;
import com.leetmodel.common.cache.CacheVersionView;
import com.leetmodel.common.cache.MultiLevelCache;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * 业务缓存未启用时的直接回源实现。
 */
public final class NoOpCacheSupport implements MultiLevelCache, CacheInvalidator, CacheVersionProvider {

    /**
     * 直接执行回源函数。
     *
     * @param spec 缓存契约
     * @param javaType 读模型类型
     * @param loader 回源函数
     * @param <T> 读模型类型
     * @return 权威数据
     */
    @Override
    public <T> T get(CacheSpec spec, JavaType javaType, Supplier<T> loader) {
        return loader.get();
    }

    /**
     * 未启用时无本地缓存需要清理。
     *
     * @param region 缓存区域
     * @param scopeKey 作用域
     */
    @Override
    public void evictLocal(String region, String scopeKey) {
    }

    /**
     * 未启用时不记录 Outbox。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     * @param schemaVersion 读模型结构版本
     */
    @Override
    public void record(String region, String scopeKey, String schemaVersion) {
    }

    /**
     * 返回按 5 秒时间桶滚动的禁用状态版本，避免客户端长期复用旧验证器。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     * @return 禁用状态版本
     */
    @Override
    public CacheVersionView current(String region, String scopeKey) {
        long bucket = Instant.now().getEpochSecond() / 5L;
        return new CacheVersionView("disabled-" + bucket, 0L, true);
    }
}
