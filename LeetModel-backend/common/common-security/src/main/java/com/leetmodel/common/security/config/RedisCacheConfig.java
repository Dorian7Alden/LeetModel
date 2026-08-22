package com.leetmodel.common.security.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存基础设施配置 —— CacheManager + 序列化策略。
 *
 * <p>职责：
 * <ul>
 *   <li>配置 {@link RedisCacheManager}，使 {@code @Cacheable} 等 Spring Cache 注解可用</li>
 *   <li>统一 Key/Value 序列化方式（String Key + JSON Value）</li>
 *   <li>设置默认 TTL 为 30 分钟，防止缓存无限增长</li>
 * </ul>
 * </p>
 *
 * <h3>面试考点</h3>
 * <ul>
 *   <li><b>为什么不用 JDK 序列化？</b> JDK 序列化要求类实现 Serializable，且二进制不可读。
 *       JSON 序列化可读性好、跨语言，是微服务缓存的标准选择。</li>
 *   <li><b>{@code Jackson2JsonRedisSerializer} vs {@code GenericJackson2JsonRedisSerializer}</b>：
 *       前者需要指定类型（如 {@code Object.class}），序列化时不写入类名；后者会在 JSON 中写入
 *       {@code @class} 字段，支持反序列化回具体类型，但占用更多空间且有安全风险。</li>
 *   <li><b>ObjectMapper 激活默认类型（Default Typing）</b>：
 *       {@code @Cacheable} 返回值可能是泛型 {@code List<User>}，反序列化时 Jackson 需要知道目标类型。
 *       设置 {@code activateDefaultTyping} 使 JSON 中携带类型信息，Jackson 才能正确还原泛型对象。</li>
 *   <li><b>缓存穿透/击穿/雪崩</b>：穿透=查不存在的数据（布隆过滤器/缓存空值），
 *       击穿=热点 key 过期（互斥锁/永不过期），雪崩=大量 key 同时过期（TTL 加随机偏移）。</li>
 * </ul>
 *
 * @author LeetModel
 */
@Configuration
public class RedisCacheConfig {

    /** 默认缓存过期时间：30 分钟 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * 配置 RedisCacheManager。
     *
     * <p>使用 {@link RedisCacheManager#create(RedisConnectionFactory)} 的变体 ——
     * 设置默认配置 + 允许按缓存名单独配置 TTL。</p>
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        // 复制一份 ObjectMapper 用于 Redis 序列化（不影响 Web 序列化配置）
        ObjectMapper redisMapper = objectMapper.copy();
        // 激活默认类型：序列化时将对象类型写入 JSON，反序列化时根据类型还原
        // NON_FINAL = 对非 final 类型的属性写入类型信息
        redisMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // JSON 序列化器（使用定制的 ObjectMapper）
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(redisMapper, Object.class);

        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // Key 序列化为字符串（可读性好）
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                // Value 序列化为 JSON
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                // 默认 TTL + 随机 10% 偏移防雪崩
                .entryTtl(DEFAULT_TTL)
                // 禁止缓存 null 值（防穿透需业务层显式决定是否缓存 null）
                .disableCachingNullValues()
                // Key 前缀（自动加 "leetmodel:" 前缀与手动 cacheNames 拼接）
                .prefixCacheNameWith("leetmodel:");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                // 可按 cacheName 单独配置 TTL，示例：
                // .withCacheConfiguration("shortLived", defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }

    /**
     * 获取默认的缓存过期时间。
     * 供业务代码中按需调整 —— {@code @Cacheable} 注解无法动态设置 TTL 时，可编程式操作。
     */
    public static Duration getDefaultTtl() {
        return DEFAULT_TTL;
    }
}
