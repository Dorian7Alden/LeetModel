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

        @Bean
        public CacheNamespace cacheNamespace(
                CacheProperties properties,
                @Value("${spring.application.name}") String applicationName
        ) {
            return new CacheNamespace(properties.getEnvironment(), applicationName);
        }

        @Bean(destroyMethod = "close")
        public BusinessRedisClient businessRedisClient(
                CacheProperties properties,
                CacheNamespace namespace,
                ObjectMapper objectMapper
        ) {
            return new BusinessRedisClient(properties, namespace, objectMapper);
        }

        @Bean
        public CacheCoordinator cacheCoordinator(
                BusinessRedisClient redisClient,
                CacheNamespace namespace,
                ObjectMapper objectMapper
        ) {
            return new CacheCoordinator(redisClient, namespace, objectMapper);
        }

        @Bean
        public CacheMetrics cacheMetrics(ObjectProvider<MeterRegistry> registryProvider) {
            return new CacheMetrics(registryProvider.getIfAvailable());
        }

        @Bean
        public CacheOutboxRepository cacheOutboxRepository(
                JdbcTemplate jdbcTemplate,
                CacheNamespace namespace
        ) {
            return new CacheOutboxRepository(jdbcTemplate, namespace);
        }

        @Bean
        public OutboxDispatcher outboxDispatcher(
                CacheOutboxRepository repository,
                CacheCoordinator coordinator
        ) {
            return new OutboxDispatcher(repository, coordinator);
        }

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

        @Bean
        public CacheInvalidator cacheInvalidator(
                CacheOutboxRepository repository,
                CacheCoordinator coordinator,
                OutboxDispatcher dispatcher
        ) {
            return new TransactionalCacheInvalidator(repository, coordinator, dispatcher);
        }

        @Bean(name = "businessCacheHealthIndicator")
        public HealthIndicator businessCacheHealthIndicator(
                BusinessRedisClient redisClient,
                CacheOutboxRepository repository
        ) {
            return new BusinessCacheHealthIndicator(redisClient, repository);
        }
    }
}
