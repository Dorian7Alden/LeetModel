package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.cache.config.CacheNamespace;
import com.leetmodel.common.cache.config.CacheProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 使用独立 Lettuce 连接访问业务缓存 Redis。
 */
@Slf4j
public final class BusinessRedisClient implements AutoCloseable {

    private static final String APPLY_INVALIDATION_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if (not current) or (tonumber(ARGV[1]) > tonumber(current)) then
              redis.call('SET', KEYS[1], ARGV[1])
              redis.call('PUBLISH', KEYS[2], ARGV[2])
              return 1
            end
            return 0
            """;

    private final RedisClient client;
    private final CacheProperties properties;
    private final CacheNamespace namespace;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<Consumer<String>> messageConsumers = new CopyOnWriteArrayList<>();
    private final Object connectionMonitor = new Object();

    private volatile StatefulRedisConnection<String, String> commandConnection;
    private volatile StatefulRedisPubSubConnection<String, String> subscriptionConnection;

    /**
     * 创建惰连接业务 Redis 客户端。
     *
     * @param properties 缓存配置
     * @param namespace Key 命名空间
     * @param objectMapper JSON 编解码器
     */
    public BusinessRedisClient(
            CacheProperties properties,
            CacheNamespace namespace,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.namespace = namespace;
        this.objectMapper = objectMapper;
        this.client = RedisClient.create(redisUri(properties.getRedis()));
        this.client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(properties.getRedis().getConnectTimeout())
                        .build())
                .timeoutOptions(TimeoutOptions.builder()
                        .fixedTimeout(properties.getRedis().getCommandTimeout())
                        .build())
                .build());
    }

    /**
     * 读取字符串值。
     *
     * @param key Redis Key
     * @return 字符串值
     */
    String get(String key) {
        return execute(commands -> commands.get(key));
    }

    /**
     * 写入带 TTL 的字符串值。
     *
     * @param key Redis Key
     * @param value Redis Value
     * @param ttl 过期时间
     */
    void set(String key, String value, Duration ttl) {
        execute(commands -> commands.setex(key, Math.max(1L, ttl.toSeconds()), value));
    }

    /**
     * 删除损坏或不再可用的缓存值。
     *
     * @param key Redis Key
     */
    void delete(String key) {
        execute(commands -> commands.del(key));
    }

    /**
     * 读取或创建 128 位随机缓存代际。
     *
     * @return 缓存代际
     */
    String generation() {
        String key = namespace.generationKey();
        String current = get(key);
        if (current != null && !current.isBlank()) return current;
        String candidate = UUID.randomUUID().toString().replace("-", "");
        execute(commands -> commands.set(key, candidate, SetArgs.Builder.nx()));
        String selected = get(key);
        if (selected == null || selected.isBlank()) {
            throw new RedisUnavailableException(new IllegalStateException("cache generation was not created"));
        }
        return selected;
    }

    /**
     * 读取区域版本。
     *
     * @param generation 缓存代际
     * @param region 缓存区域
     * @param scopeKey 作用域
     * @return 区域版本；未建立时为零
     */
    long revision(String generation, String region, String scopeKey) {
        String value = get(namespace.revisionKey(generation, region, scopeKey));
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new RedisUnavailableException(exception);
        }
    }

    /**
     * 幂等应用失效事件版本并发布通知。
     *
     * @param event 携带当前代际的失效事件
     */
    void applyInvalidation(CacheInvalidationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String revisionKey = namespace.revisionKey(
                    event.generation(), event.region(), event.scopeKey());
            execute(commands -> commands.eval(
                    APPLY_INVALIDATION_SCRIPT,
                    ScriptOutputType.INTEGER,
                    new String[]{revisionKey, namespace.invalidationChannel()},
                    Long.toString(event.revision()),
                    payload
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cache invalidation event cannot be serialized", exception);
        }
    }

    /**
     * 校验 Redis 可用性。
     *
     * @return 是否可用
     */
    boolean ping() {
        return "PONG".equals(execute(RedisCommands::ping));
    }

    /**
     * 注册失效消息消费者。
     *
     * @param consumer 消费者
     */
    void addMessageConsumer(Consumer<String> consumer) {
        messageConsumers.add(consumer);
    }

    /**
     * 确保 Pub/Sub 连接已订阅失效频道。
     */
    void ensureSubscribed() {
        StatefulRedisPubSubConnection<String, String> current = subscriptionConnection;
        if (current != null && current.isOpen()) return;
        synchronized (connectionMonitor) {
            current = subscriptionConnection;
            if (current != null && current.isOpen()) return;
            try {
                StatefulRedisPubSubConnection<String, String> created = client.connectPubSub();
                created.addListener(new RedisPubSubAdapter<>() {
                    @Override
                    public void message(String channel, String message) {
                        if (!namespace.invalidationChannel().equals(channel)) return;
                        for (Consumer<String> consumer : messageConsumers) consumer.accept(message);
                    }
                });
                created.sync().subscribe(namespace.invalidationChannel());
                subscriptionConnection = created;
            } catch (RuntimeException exception) {
                closeSubscription();
                throw new RedisUnavailableException(exception);
            }
        }
    }

    /**
     * 执行 Redis 命令并统一转换故障。
     *
     * @param action Redis 命令
     * @param <T> 返回类型
     * @return 命令结果
     */
    private <T> T execute(Function<RedisCommands<String, String>, T> action) {
        try {
            return action.apply(commandConnection().sync());
        } catch (RuntimeException exception) {
            closeCommandConnection();
            throw new RedisUnavailableException(exception);
        }
    }

    /**
     * 获取或重建命令连接。
     *
     * @return Lettuce 命令连接
     */
    private StatefulRedisConnection<String, String> commandConnection() {
        StatefulRedisConnection<String, String> current = commandConnection;
        if (current != null && current.isOpen()) return current;
        synchronized (connectionMonitor) {
            current = commandConnection;
            if (current != null && current.isOpen()) return current;
            try {
                commandConnection = client.connect();
                return commandConnection;
            } catch (RuntimeException exception) {
                throw new RedisUnavailableException(exception);
            }
        }
    }

    /**
     * 创建 Lettuce Redis URI。
     *
     * @param redis Redis 配置
     * @return Redis URI
     */
    private static RedisURI redisUri(CacheProperties.Redis redis) {
        RedisURI.Builder builder = RedisURI.Builder.redis(redis.getHost(), redis.getPort())
                .withDatabase(redis.getDatabase())
                .withTimeout(redis.getCommandTimeout())
                .withSsl(redis.isSsl());
        if (redis.getPassword() != null && !redis.getPassword().isBlank()) {
            builder.withPassword(redis.getPassword().toCharArray());
        }
        return builder.build();
    }

    /**
     * 关闭命令连接。
     */
    private void closeCommandConnection() {
        StatefulRedisConnection<String, String> current = commandConnection;
        commandConnection = null;
        if (current == null) return;
        try {
            current.close();
        } catch (RuntimeException exception) {
            log.debug("关闭业务 Redis 命令连接失败", exception);
        }
    }

    /**
     * 关闭订阅连接。
     */
    private void closeSubscription() {
        StatefulRedisPubSubConnection<String, String> current = subscriptionConnection;
        subscriptionConnection = null;
        if (current == null) return;
        try {
            current.close();
        } catch (RuntimeException exception) {
            log.debug("关闭业务 Redis 订阅连接失败", exception);
        }
    }

    /**
     * 关闭全部 Redis 资源。
     */
    @Override
    public void close() {
        closeSubscription();
        closeCommandConnection();
        client.shutdown();
    }
}
