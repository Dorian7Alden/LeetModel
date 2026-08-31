package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.cache.CacheVersionProvider;
import com.leetmodel.common.cache.CacheVersionView;
import com.leetmodel.common.cache.config.CacheNamespace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 协调缓存代际、区域版本、Pub/Sub 与定时对账。
 */
@Slf4j
public final class CacheCoordinator implements CacheVersionProvider {

    private static final long DEGRADED_BUCKET_SECONDS = 5L;

    private final BusinessRedisClient redisClient;
    private final CacheNamespace namespace;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, ScopeVersion> observedVersions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CacheStateListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean redisAvailable = true;
    private volatile String observedGeneration;

    /**
     * 创建缓存版本协调器。
     *
     * @param redisClient 业务 Redis 客户端
     * @param namespace 缓存命名空间
     * @param objectMapper JSON 编解码器
     */
    public CacheCoordinator(
            BusinessRedisClient redisClient,
            CacheNamespace namespace,
            ObjectMapper objectMapper
    ) {
        this(redisClient, namespace, objectMapper, Clock.systemUTC());
    }

    /**
     * 创建可注入时钟的缓存版本协调器。
     *
     * @param redisClient 业务 Redis 客户端
     * @param namespace 缓存命名空间
     * @param objectMapper JSON 编解码器
     * @param clock 时钟
     */
    CacheCoordinator(
            BusinessRedisClient redisClient,
            CacheNamespace namespace,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.redisClient = redisClient;
        this.namespace = namespace;
        this.objectMapper = objectMapper;
        this.clock = clock;
        redisClient.addMessageConsumer(this::onMessage);
    }

    /**
     * 注册本地缓存状态监听器。
     *
     * @param listener 监听器
     */
    public void addListener(CacheStateListener listener) {
        listeners.add(listener);
    }

    /**
     * 获取当前观测版本，首次访问时从 Redis 初始化。
     *
     * @param region 缓存区域
     * @param scopeKey 作用域
     * @return 当前版本
     */
    ScopeVersion observed(String region, String scopeKey) {
        if (!redisAvailable) throw new RedisUnavailableException(new IllegalStateException("redis is degraded"));
        String scopeId = scopeId(region, scopeKey);
        ScopeVersion current = observedVersions.get(scopeId);
        if (current != null) return current;
        return refresh(region, scopeKey);
    }

    /**
     * 强制从 Redis 读取指定区域版本。
     *
     * @param region 缓存区域
     * @param scopeKey 作用域
     * @return Redis 中的版本
     */
    ScopeVersion refresh(String region, String scopeKey) {
        try {
            String generation = redisClient.generation();
            handleGeneration(generation);
            long revision = redisClient.revision(generation, region, scopeKey);
            ScopeVersion refreshed = new ScopeVersion(generation, revision);
            merge(scopeId(region, scopeKey), refreshed);
            markAvailable();
            return refreshed;
        } catch (RedisUnavailableException exception) {
            markUnavailable(exception);
            throw exception;
        }
    }

    /**
     * 接收读取链路中发现的 Redis 故障并切换降级状态。
     *
     * @param exception Redis 故障
     */
    void redisUnavailable(RedisUnavailableException exception) {
        markUnavailable(exception);
    }

    /**
     * 接收本服务已提交但可能尚未投递的事件版本。
     *
     * @param event 失效事件
     */
    void acceptCommitted(CacheInvalidationEvent event) {
        String generation = observedGeneration;
        if (generation == null) {
            try {
                generation = redisClient.generation();
                handleGeneration(generation);
            } catch (RedisUnavailableException exception) {
                markUnavailable(exception);
                generation = degradedGeneration();
            }
        }
        merge(event.scopeId(), new ScopeVersion(generation, event.revision()));
        notifyScopeChanged(event.region(), event.scopeKey());
    }

    /**
     * 将 Outbox 事件幂等应用到 Redis。
     *
     * @param event 失效事件
     */
    void publish(CacheInvalidationEvent event) {
        try {
            String generation = redisClient.generation();
            handleGeneration(generation);
            CacheInvalidationEvent deliverable = event.withGeneration(generation);
            redisClient.applyInvalidation(deliverable);
            merge(event.scopeId(), new ScopeVersion(generation, event.revision()));
            markAvailable();
        } catch (RedisUnavailableException exception) {
            markUnavailable(exception);
            throw exception;
        }
    }

    /**
     * 读取用于 HTTP ETag 的版本。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     * @return HTTP 可见版本
     */
    @Override
    public CacheVersionView current(String region, String scopeKey) {
        try {
            ScopeVersion version = observed(region, scopeKey);
            return new CacheVersionView(version.generation(), version.revision(), false);
        } catch (RedisUnavailableException exception) {
            long bucket = clock.instant().getEpochSecond() / DEGRADED_BUCKET_SECONDS;
            return new CacheVersionView("degraded-" + bucket, 0L, true);
        }
    }

    /**
     * 每五秒对账已观测区域，并维持 Pub/Sub 订阅。
     */
    @Scheduled(fixedDelayString = "${leetmodel.cache.reconcile-interval:5000}")
    public void reconcile() {
        try {
            redisClient.ping();
            redisClient.ensureSubscribed();
            String generation = redisClient.generation();
            handleGeneration(generation);
            for (Map.Entry<String, ScopeVersion> entry : new ArrayList<>(observedVersions.entrySet())) {
                String[] scope = splitScope(entry.getKey());
                long revision = redisClient.revision(generation, scope[0], scope[1]);
                ScopeVersion remote = new ScopeVersion(generation, revision);
                if (!remote.equals(entry.getValue())) {
                    merge(entry.getKey(), remote);
                    notifyScopeChanged(scope[0], scope[1]);
                }
            }
            markAvailable();
        } catch (RedisUnavailableException exception) {
            markUnavailable(exception);
        }
    }

    /**
     * 处理 Pub/Sub 失效消息。
     *
     * @param payload JSON 消息
     */
    private void onMessage(String payload) {
        try {
            CacheInvalidationEvent event = objectMapper.readValue(payload, CacheInvalidationEvent.class);
            if (!namespace.ownerService().equals(event.ownerService())) return;
            handleGeneration(event.generation());
            String scopeId = event.scopeId();
            ScopeVersion incoming = new ScopeVersion(event.generation(), event.revision());
            ScopeVersion current = observedVersions.get(scopeId);
            if (current != null
                    && current.generation().equals(incoming.generation())
                    && current.revision() >= incoming.revision()) return;
            merge(scopeId, incoming);
            notifyScopeChanged(event.region(), event.scopeKey());
        } catch (Exception exception) {
            log.warn("忽略无法解析的缓存失效消息", exception);
        }
    }

    /**
     * 处理 Redis 代际切换。
     *
     * @param generation 当前代际
     */
    private synchronized void handleGeneration(String generation) {
        if (generation.equals(observedGeneration)) return;
        observedGeneration = generation;
        observedVersions.clear();
        for (CacheStateListener listener : listeners) listener.onGenerationChanged();
    }

    /**
     * 合并同代际的更大版本。
     *
     * @param scopeId 作用域标识
     * @param incoming 新版本
     */
    private void merge(String scopeId, ScopeVersion incoming) {
        observedVersions.compute(scopeId, (ignored, current) -> {
            if (current == null) return incoming;
            if (!current.generation().equals(incoming.generation())) return incoming;
            return incoming.revision() > current.revision() ? incoming : current;
        });
    }

    /**
     * 通知本地缓存清理指定作用域。
     *
     * @param region 区域
     * @param scopeKey 作用域
     */
    private void notifyScopeChanged(String region, String scopeKey) {
        for (CacheStateListener listener : listeners) listener.onScopeChanged(region, scopeKey);
    }

    /**
     * 标记 Redis 恢复。
     */
    private void markAvailable() {
        if (redisAvailable) return;
        redisAvailable = true;
        for (CacheStateListener listener : listeners) listener.onRedisRecovered();
        log.info("业务 Redis 已恢复，多级缓存重新启用");
    }

    /**
     * 标记 Redis 降级并清理常规本地缓存。
     *
     * @param exception Redis 故障
     */
    private void markUnavailable(RuntimeException exception) {
        if (!redisAvailable) return;
        redisAvailable = false;
        for (CacheStateListener listener : listeners) listener.onRedisUnavailable();
        log.warn("业务 Redis 不可用，已切换五秒本地降级缓存: {}",
                exception.getMessage());
    }

    /**
     * 返回当前降级代际。
     *
     * @return 降级代际
     */
    private String degradedGeneration() {
        return "degraded-" + clock.instant().getEpochSecond() / DEGRADED_BUCKET_SECONDS;
    }

    /**
     * 组合作用域标识。
     *
     * @param region 区域
     * @param scopeKey 作用域
     * @return 作用域标识
     */
    private String scopeId(String region, String scopeKey) {
        return region + ":" + scopeKey;
    }

    /**
     * 拆分作用域标识。
     *
     * @param scopeId 作用域标识
     * @return 区域和作用域
     */
    private String[] splitScope(String scopeId) {
        return scopeId.split(":", 2);
    }

    /**
     * 当前观测的缓存代际和区域版本。
     *
     * @param generation 缓存代际
     * @param revision 区域版本
     */
    record ScopeVersion(String generation, long revision) {
    }

    /**
     * 本地缓存状态变更监听器。
     */
    public interface CacheStateListener {

        /**
         * 区域版本变更。
         *
         * @param region 区域
         * @param scopeKey 作用域
         */
        void onScopeChanged(String region, String scopeKey);

        /**
         * Redis 不可用。
         */
        void onRedisUnavailable();

        /**
         * Redis 恢复。
         */
        void onRedisRecovered();

        /**
         * Redis 缓存代际改变。
         */
        void onGenerationChanged();
    }
}
