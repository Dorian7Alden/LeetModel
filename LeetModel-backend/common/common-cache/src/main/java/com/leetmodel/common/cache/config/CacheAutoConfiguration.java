package com.leetmodel.common.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.common.cache.CacheVersionProvider;
import com.leetmodel.common.cache.MultiLevelCache;
import com.leetmodel.common.cache.internal.BusinessCacheHealthIndicator;
import com.leetmodel.common.cache.internal.BusinessRedisClient;
import com.leetmodel.common.cache.internal.CacheCoordinator;
import com.leetmodel.common.cache.internal.CacheMetrics;
import com.leetmodel.common.cache.internal.CacheOutboxRepository;
import com.leetmodel.common.cache.internal.NoOpCacheSupport;
import com.leetmodel.common.cache.internal.OutboxDispatcher;
import com.leetmodel.common.cache.internal.TieredMultiLevelCache;
import com.leetmodel.common.cache.internal.TransactionalCacheInvalidator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `common-cache` Spring Boot 自动配置。
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * 业务缓存禁用时提供直接回源实现。
     *
     * @return 无缓存实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "leetmodel.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
    public NoOpCacheSupport noOpCacheSupport() {
        return new NoOpCacheSupport();
    }

    /**
     * 将无缓存实现暴露为多级缓存入口。
     *
     * @param support 无缓存实现
     * @return 多级缓存入口
     */
    @Bean
    @ConditionalOnMissingBean(MultiLevelCache.class)
    @ConditionalOnProperty(prefix = "leetmodel.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
    public MultiLevelCache noOpMultiLevelCache(NoOpCacheSupport support) {
        return support;
    }

    /**
     * 将无缓存实现暴露为失效入口。
     *
     * @param support 无缓存实现
     * @return 失效入口
     */
    @Bean
    @ConditionalOnMissingBean(CacheInvalidator.class)
    @ConditionalOnProperty(prefix = "leetmodel.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
    public CacheInvalidator noOpCacheInvalidator(NoOpCacheSupport support) {
        return support;
    }

    /**
     * 将无缓存实现暴露为版本入口。
     *
     * @param support 无缓存实现
     * @return 版本入口
     */
    @Bean
    @ConditionalOnMissingBean(CacheVersionProvider.class)
    @ConditionalOnProperty(prefix = "leetmodel.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
    public CacheVersionProvider noOpCacheVersionProvider(NoOpCacheSupport support) {
        return support;
    }

    /**
     * 业务缓存启用时的基础组件。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "leetmodel.cache", name = "enabled", havingValue = "true")
    public static class EnabledCacheConfiguration {

        /**
         * 注册缓存命名空间 Bean。
         *
         * @param properties      缓存属性配置
         * @param applicationName 当前微服务应用名称
         * @return 注入了环境与服务名的 CacheNamespace 实例
         */
        @Bean
        public CacheNamespace cacheNamespace(
                CacheProperties properties,
                @Value("${spring.application.name}") String applicationName
        ) {
            return new CacheNamespace(properties.getEnvironment(), applicationName);
        }

        /**
         * 注册业务专用 Redis 客户端 Bean。
         *
         * @param properties   缓存属性配置
         * @param namespace    缓存命名空间
         * @param objectMapper JSON 序列化映射器
         * @return 具备独立连接与 Pub/Sub 能力的 BusinessRedisClient 实例
         */
        @Bean(destroyMethod = "close")
        public BusinessRedisClient businessRedisClient(
                CacheProperties properties,
                CacheNamespace namespace,
                ObjectMapper objectMapper
        ) {
            return new BusinessRedisClient(properties, namespace, objectMapper);
        }

        /**
         * 注册缓存版本协调器 Bean。
         *
         * @param redisClient  业务 Redis 客户端
         * @param namespace    缓存命名空间
         * @param objectMapper JSON 序列化映射器
         * @return 负责管理代际版本与 Pub/Sub 监听的 CacheCoordinator 实例
         */
        @Bean
        public CacheCoordinator cacheCoordinator(
                BusinessRedisClient redisClient,
                CacheNamespace namespace,
                ObjectMapper objectMapper
        ) {
            return new CacheCoordinator(redisClient, namespace, objectMapper);
        }

        /**
         * 注册多级缓存性能监控指标收集器 Bean。
         *
         * @param registryProvider 可选的 Micrometer 注册中心提供者
         * @return CacheMetrics 实例
         */
        @Bean
        public CacheMetrics cacheMetrics(ObjectProvider<MeterRegistry> registryProvider) {
            return new CacheMetrics(registryProvider.getIfAvailable());
        }

        /**
         * 注册缓存失效事务 Outbox 仓储 Bean。
         *
         * @param jdbcTemplate 数据库 JDBC 操作模板
         * @param namespace    缓存命名空间
         * @return 负责本地事务表写入与扫表的 CacheOutboxRepository 实例
         */
        @Bean
        public CacheOutboxRepository cacheOutboxRepository(
                JdbcTemplate jdbcTemplate,
                CacheNamespace namespace
        ) {
            return new CacheOutboxRepository(jdbcTemplate, namespace);
        }

        /**
         * 注册缓存 Outbox 异步派发器 Bean。
         *
         * @param repository  缓存 Outbox 仓储
         * @param coordinator 缓存版本协调器
         * @return 周期扫表并向 Redis 投递失效广播的 OutboxDispatcher 实例
         */
        @Bean
        public OutboxDispatcher outboxDispatcher(
                CacheOutboxRepository repository,
                CacheCoordinator coordinator
        ) {
            return new OutboxDispatcher(repository, coordinator);
        }

        /**
         * 注册三级多级缓存主服务 Bean。
         *
         * @param redisClient  业务 Redis 客户端
         * @param coordinator  缓存版本协调器
         * @param namespace    缓存命名空间
         * @param properties   缓存属性配置
         * @param objectMapper JSON 序列化映射器
         * @param metrics      缓存指标收集器
         * @return 整合了 Caffeine L1、Redis L2 与事实源回退的 MultiLevelCache 实例
         */
        @Bean
        public MultiLevelCache multiLevelCache(
                BusinessRedisClient redisClient,
                CacheCoordinator coordinator,
                CacheNamespace namespace,
                CacheProperties properties,
                ObjectMapper objectMapper,
                CacheMetrics metrics
        ) {
            return new TieredMultiLevelCache(
                    redisClient,
                    coordinator,
                    namespace,
                    properties,
                    objectMapper,
                    metrics
            );
        }

        /**
         * 注册事务内缓存失效记录器 Bean。
         *
         * @param repository  缓存 Outbox 仓储
         * @param coordinator 缓存版本协调器
         * @param dispatcher  Outbox 异步派发器
         * @return 配合业务事务写入 cache_invalidation_outbox 的 CacheInvalidator 实例
         */
        @Bean
        public CacheInvalidator cacheInvalidator(
                CacheOutboxRepository repository,
                CacheCoordinator coordinator,
                OutboxDispatcher dispatcher
        ) {
            return new TransactionalCacheInvalidator(repository, coordinator, dispatcher);
        }

        /**
         * 注册业务缓存健康检查指示器 Bean。
         *
         * @param redisClient 业务 Redis 客户端
         * @param repository  缓存 Outbox 仓储
         * @return 供 Actuator 探测缓存降级状态的 HealthIndicator 实例
         */
        @Bean(name = "businessCacheHealthIndicator")
        public HealthIndicator businessCacheHealthIndicator(
                BusinessRedisClient redisClient,
                CacheOutboxRepository repository
        ) {
            return new BusinessCacheHealthIndicator(redisClient, repository);
        }
    }
}
