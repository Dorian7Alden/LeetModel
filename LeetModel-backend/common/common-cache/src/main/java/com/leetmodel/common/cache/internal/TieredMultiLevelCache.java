package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.leetmodel.common.cache.CacheSpec;
import com.leetmodel.common.cache.MultiLevelCache;
import com.leetmodel.common.cache.config.CacheNamespace;
import com.leetmodel.common.cache.config.CacheProperties;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Caffeine、Redis、MySQL 回源的编程式 Cache Aside 实现。
 */
public final class TieredMultiLevelCache implements MultiLevelCache, CacheCoordinator.CacheStateListener {

    private static final int RETRY_LIMIT = 1;

    private final BusinessRedisClient redisClient;
    private final CacheCoordinator coordinator;
    private final CacheNamespace namespace;
    private final CacheProperties properties;
    private final ObjectMapper objectMapper;
    private final CacheMetrics metrics;
    private final Cache<String, LocalValue> localCache;
    private final Cache<String, LocalValue> degradedCache;

    /**
     * 创建三级缓存实现。
     *
     * @param redisClient 业务 Redis 客户端
     * @param coordinator 缓存版本协调器
     * @param namespace 缓存命名空间
     * @param properties 缓存配置
     * @param objectMapper JSON 编解码器
     * @param metrics 缓存指标
     */
    public TieredMultiLevelCache(
            BusinessRedisClient redisClient,
            CacheCoordinator coordinator,
            CacheNamespace namespace,
            CacheProperties properties,
            ObjectMapper objectMapper,
            CacheMetrics metrics
    ) {
        this.redisClient = redisClient;
        this.coordinator = coordinator;
        this.namespace = namespace;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.localCache = buildLocalCache(properties.getMaximumWeight());
        this.degradedCache = buildLocalCache(Math.max(1024L, properties.getMaximumWeight() / 8L));
        coordinator.addListener(this);
    }

    /**
     * 按 L2、L3、回源函数的顺序读取强类型读模型。
     *
     * @param spec 缓存契约
     * @param javaType 显式反序列化类型
     * @param loader 权威数据回源函数
     * @param <T> 读模型类型
     * @return 读模型；不存在时为 null
     */
    @Override
    public <T> T get(CacheSpec spec, JavaType javaType, Supplier<T> loader) {
        for (int attempt = 0; attempt <= RETRY_LIMIT; attempt++) {
            try {
                return normalGet(spec, javaType, loader);
            } catch (VersionChangedException exception) {
                if (attempt == RETRY_LIMIT) return loadWithoutCaching(spec, loader);
            } catch (SourceLoadedRedisUnavailableException exception) {
                return cacheDegradedValue(spec, exception.value(), exception.bytes());
            } catch (RedisUnavailableException exception) {
                coordinator.redisUnavailable(exception);
                return degradedGet(spec, loader);
            } catch (BypassValueException exception) {
                return cast(exception.value());
            }
        }
        return loadWithoutCaching(spec, loader);
    }

    /**
     * 清理指定区域作用域的本地值。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     */
    @Override
    public void evictLocal(String region, String scopeKey) {
        String prefix = region + ":" + scopeKey + ":";
        evictByPrefix(localCache, prefix);
        evictByPrefix(degradedCache, prefix);
    }

    /**
     * 处理区域版本变更。
     *
     * @param region 区域
     * @param scopeKey 作用域
     */
    @Override
    public void onScopeChanged(String region, String scopeKey) {
        evictLocal(region, scopeKey);
    }

    /**
     * Redis 不可用时立即丢弃常规 L2。
     */
    @Override
    public void onRedisUnavailable() {
        localCache.invalidateAll();
    }

    /**
     * Redis 恢复后丢弃紧急降级值。
     */
    @Override
    public void onRedisRecovered() {
        degradedCache.invalidateAll();
    }

    /**
     * Redis 代际变化时清理全部 L2。
     */
    @Override
    public void onGenerationChanged() {
        localCache.invalidateAll();
        degradedCache.invalidateAll();
    }

    /**
     * 执行常规版本化读取。
     *
     * @param spec 缓存契约
     * @param javaType 读模型类型
     * @param loader 回源函数
     * @param <T> 读模型类型
     * @return 读模型
     */
    private <T> T normalGet(CacheSpec spec, JavaType javaType, Supplier<T> loader) {
        CacheCoordinator.ScopeVersion version = coordinator.observed(spec.region(), spec.scopeKey());
        String key = localKey(spec, version);
        LocalValue existing = localCache.getIfPresent(key);
        if (existing != null) {
            metrics.read(spec.region(), "l2", "hit");
            return cast(existing.value());
        }
        metrics.read(spec.region(), "l2", "miss");
        LocalValue loaded = localCache.get(key, ignored -> loadNormal(spec, javaType, loader, version));
        return cast(loaded.value());
    }

    /**
     * 从 Redis 或权威数据源加载值。
     *
     * @param spec 缓存契约
     * @param javaType 读模型类型
     * @param loader 回源函数
     * @param version 读取开始时的版本
     * @param <T> 读模型类型
     * @return 本地缓存值
     */
    private <T> LocalValue loadNormal(
            CacheSpec spec,
            JavaType javaType,
            Supplier<T> loader,
            CacheCoordinator.ScopeVersion version
    ) {
        String redisKey = namespace.dataKey(
                version.generation(),
                spec.region(),
                spec.scopeKey(),
                spec.schemaVersion(),
                version.revision(),
                spec.logicalKey()
        );
        String cachedJson = redisClient.get(redisKey);
        if (cachedJson != null) {
            try {
                DecodedValue decoded = decode(cachedJson, javaType);
                metrics.read(spec.region(), "l3", "hit");
                return new LocalValue(
                        decoded.value(),
                        cachedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        jitter(decoded.present() ? spec.localTtl() : spec.localNegativeTtl())
                );
            } catch (IllegalStateException exception) {
                metrics.read(spec.region(), "l3", "corrupt");
                redisClient.delete(redisKey);
            }
        }

        metrics.read(spec.region(), "l3", "miss");
        long startedAt = System.nanoTime();
        T sourceValue = loader.get();
        metrics.load(spec.region(), Duration.ofNanos(System.nanoTime() - startedAt));
        EncodedValue encoded = encode(sourceValue);
        if (encoded.bytes() > properties.getMaximumValueBytes()) {
            metrics.skipped(spec.region(), "oversized");
            throw new BypassValueException(sourceValue);
        }

        try {
            CacheCoordinator.ScopeVersion afterLoad = coordinator.refresh(spec.region(), spec.scopeKey());
            if (!afterLoad.equals(version)) throw new VersionChangedException();
            Duration redisTtl = jitter(sourceValue == null ? spec.redisNegativeTtl() : spec.redisTtl());
            redisClient.set(redisKey, encoded.json(), redisTtl);
        } catch (RedisUnavailableException exception) {
            coordinator.redisUnavailable(exception);
            throw new SourceLoadedRedisUnavailableException(sourceValue, encoded.bytes());
        }
        return new LocalValue(
                sourceValue,
                encoded.bytes(),
                jitter(sourceValue == null ? spec.localNegativeTtl() : spec.localTtl())
        );
    }

    /**
     * Redis 不可用时使用独立五秒本地缓存。
     *
     * @param spec 缓存契约
     * @param loader 回源函数
     * @param <T> 读模型类型
     * @return 读模型
     */
    private <T> T degradedGet(CacheSpec spec, Supplier<T> loader) {
        String key = spec.scopeId() + ":" + spec.schemaVersion() + ":" + spec.logicalKey();
        LocalValue existing = degradedCache.getIfPresent(key);
        if (existing != null) {
            metrics.read(spec.region(), "degraded", "hit");
            return cast(existing.value());
        }
        metrics.read(spec.region(), "degraded", "miss");
        try {
            LocalValue loaded = degradedCache.get(key, ignored -> {
                long startedAt = System.nanoTime();
                T sourceValue = loader.get();
                metrics.load(spec.region(), Duration.ofNanos(System.nanoTime() - startedAt));
                EncodedValue encoded = encode(sourceValue);
                if (encoded.bytes() > properties.getMaximumValueBytes()) {
                    throw new BypassValueException(sourceValue);
                }
                return new LocalValue(sourceValue, encoded.bytes(), properties.getDegradedTtl());
            });
            return cast(loaded.value());
        } catch (BypassValueException exception) {
            metrics.skipped(spec.region(), "oversized");
            return cast(exception.value());
        }
    }

    /**
     * Redis 在回源完成后故障时复用本次结果，避免再次查询数据库。
     *
     * @param spec 缓存契约
     * @param value 已回源的值
     * @param bytes 编码字节数
     * @param <T> 读模型类型
     * @return 已回源的值
     */
    private <T> T cacheDegradedValue(CacheSpec spec, Object value, int bytes) {
        if (bytes > properties.getMaximumValueBytes()) {
            metrics.skipped(spec.region(), "oversized");
            return cast(value);
        }
        String key = spec.scopeId() + ":" + spec.schemaVersion() + ":" + spec.logicalKey();
        LocalValue existing = degradedCache.getIfPresent(key);
        if (existing != null) return cast(existing.value());
        degradedCache.put(key, new LocalValue(value, bytes, properties.getDegradedTtl()));
        return cast(value);
    }

    /**
     * 缓存竞态无法在有界重试内稳定时直接回源。
     *
     * @param spec 缓存契约
     * @param loader 回源函数
     * @param <T> 读模型类型
     * @return 权威数据
     */
    private <T> T loadWithoutCaching(CacheSpec spec, Supplier<T> loader) {
        metrics.skipped(spec.region(), "version-race");
        long startedAt = System.nanoTime();
        T value = loader.get();
        metrics.load(spec.region(), Duration.ofNanos(System.nanoTime() - startedAt));
        return value;
    }

    /**
     * 将强类型读模型编码为不含类名的 JSON 包装。
     *
     * @param value 读模型
     * @return JSON 和字节数
     */
    private EncodedValue encode(Object value) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("present", value != null);
        if (value != null) envelope.set("payload", objectMapper.valueToTree(value));
        try {
            String json = objectMapper.writeValueAsString(envelope);
            return new EncodedValue(
                    json,
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cache value cannot be serialized", exception);
        }
    }

    /**
     * 按调用方显式类型解码 JSON 包装。
     *
     * @param json JSON 包装
     * @param javaType 显式目标类型
     * @return 解码值
     */
    private DecodedValue decode(String json, JavaType javaType) {
        try {
            JsonNode envelope = objectMapper.readTree(json);
            boolean present = envelope.path("present").asBoolean(false);
            if (!present) return new DecodedValue(false, null);
            JsonNode payload = envelope.get("payload");
            if (payload == null || payload.isNull()) {
                throw new IllegalStateException("cache payload is missing");
            }
            return new DecodedValue(true, objectMapper.convertValue(payload, javaType));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new IllegalStateException("cache value cannot be deserialized", exception);
        }
    }

    /**
     * 创建支持每值 TTL 和权重限制的 Caffeine。
     *
     * @param maximumWeight 最大字节权重
     * @return Caffeine 缓存
     */
    private Cache<String, LocalValue> buildLocalCache(long maximumWeight) {
        return Caffeine.newBuilder()
                .maximumWeight(maximumWeight)
                .weigher((String ignored, LocalValue value) -> Math.max(1, value.weight()))
                .expireAfter(new Expiry<String, LocalValue>() {
                    @Override
                    public long expireAfterCreate(String key, LocalValue value, long currentTime) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(
                            String key,
                            LocalValue value,
                            long currentTime,
                            long currentDuration
                    ) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(
                            String key,
                            LocalValue value,
                            long currentTime,
                            long currentDuration
                    ) {
                        return currentDuration;
                    }
                })
                .recordStats()
                .build();
    }

    /**
     * 为 TTL 生成正负偏移。
     *
     * @param base 基准 TTL
     * @return 偏移后 TTL
     */
    private Duration jitter(Duration base) {
        double jitter = Math.max(0D, Math.min(properties.getTtlJitter(), 0.49D));
        if (jitter == 0D) return base;
        double factor = ThreadLocalRandom.current().nextDouble(1D - jitter, 1D + jitter);
        return Duration.ofMillis(Math.max(1L, Math.round(base.toMillis() * factor)));
    }

    /**
     * 生成本地版本化 Key。
     *
     * @param spec 缓存契约
     * @param version 当前版本
     * @return 本地 Key
     */
    private String localKey(CacheSpec spec, CacheCoordinator.ScopeVersion version) {
        return spec.scopeId() + ":g" + version.generation()
                + ":" + spec.schemaVersion()
                + ":r" + version.revision()
                + ":" + spec.logicalKey();
    }

    /**
     * 按作用域前缀清理 Caffeine。
     *
     * @param cache Caffeine
     * @param prefix 作用域前缀
     */
    private void evictByPrefix(Cache<String, LocalValue> cache, String prefix) {
        for (String key : cache.asMap().keySet()) {
            if (key.startsWith(prefix)) cache.invalidate(key);
        }
    }

    /**
     * 转换内部对象类型。
     *
     * @param value 内部值
     * @param <T> 目标类型
     * @return 目标值
     */
    @SuppressWarnings("unchecked")
    private <T> T cast(Object value) {
        return (T) value;
    }

    /** 本地缓存包装。 */
    private record LocalValue(Object value, int weight, Duration ttl) {
    }

    /** JSON 编码结果。 */
    private record EncodedValue(String json, int bytes) {
    }

    /** JSON 解码结果。 */
    private record DecodedValue(boolean present, Object value) {
    }

    /** 回源期间版本已变更。 */
    private static final class VersionChangedException extends RuntimeException {
    }

    /** 值超过容量上限并携带本次回源结果。 */
    private static final class BypassValueException extends RuntimeException {
        private final Object value;

        private BypassValueException(Object value) {
            this.value = value;
        }

        private Object value() {
            return value;
        }
    }

    /** Redis 在权威数据已读取后发生故障。 */
    private static final class SourceLoadedRedisUnavailableException extends RuntimeException {
        private final Object value;
        private final int bytes;

        private SourceLoadedRedisUnavailableException(Object value, int bytes) {
            this.value = value;
            this.bytes = bytes;
        }

        private Object value() {
            return value;
        }

        private int bytes() {
            return bytes;
        }
    }
}
