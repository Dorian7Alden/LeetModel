package com.leetmodel.common.cache;

/**
 * 在业务事务中记录可靠缓存失效事件。
 */
public interface CacheInvalidator {

    /**
     * 记录一个与当前业务事务共同提交的失效事件。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     * @param schemaVersion 读模型结构版本
     */
    void record(String region, String scopeKey, String schemaVersion);
}
