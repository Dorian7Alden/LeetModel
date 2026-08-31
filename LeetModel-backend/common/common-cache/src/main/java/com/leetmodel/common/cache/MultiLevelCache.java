package com.leetmodel.common.cache;

import com.fasterxml.jackson.databind.JavaType;

import java.util.function.Supplier;

/**
 * 强类型 Cache Aside 读取入口。
 */
public interface MultiLevelCache {

    /**
     * 按 Caffeine、Redis、回源函数的顺序读取数据。回源为 null 时使用短期空值标记。
     *
     * @param spec 缓存契约
     * @param javaType 显式反序列化类型
     * @param loader 权威数据回源函数
     * @param <T> 读模型类型
     * @return 读模型；不存在时为 null
     */
    <T> T get(CacheSpec spec, JavaType javaType, Supplier<T> loader);

    /**
     * 清理当前实例的指定区域作用域。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     */
    void evictLocal(String region, String scopeKey);
}
