package com.leetmodel.common.cache.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;

/**
 * 记录不携带业务 Key 的低基数缓存指标。
 */
public final class CacheMetrics {

    private final MeterRegistry registry;

    /**
     * 创建指标记录器。
     *
     * @param registry Micrometer 注册表；不可用时为 null
     */
    public CacheMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录一次缓存读取。
     *
     * @param region 缓存区域
     * @param layer 缓存层级
     * @param result 读取结果
     */
    void read(String region, String layer, String result) {
        if (registry == null) return;
        Counter.builder("leetmodel.cache.reads")
                .tag("region", region)
                .tag("layer", layer)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    /**
     * 记录回源耗时。
     *
     * @param region 缓存区域
     * @param duration 耗时
     */
    void load(String region, Duration duration) {
        if (registry == null) return;
        Timer.builder("leetmodel.cache.load")
                .tag("region", region)
                .register(registry)
                .record(duration);
    }

    /**
     * 记录一次缓存值跳过。
     *
     * @param region 缓存区域
     * @param reason 跳过原因
     */
    void skipped(String region, String reason) {
        if (registry == null) return;
        Counter.builder("leetmodel.cache.skipped")
                .tag("region", region)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
