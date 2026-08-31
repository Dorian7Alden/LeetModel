package com.leetmodel.common.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.common.messaging.internal.JdbcMessageOutbox;
import com.leetmodel.common.messaging.internal.MessagingHealthIndicator;
import com.leetmodel.common.messaging.internal.MessagingMetrics;
import com.leetmodel.common.messaging.internal.ObservedMessageInbox;
import com.leetmodel.common.messaging.internal.OutboxRelay;
import com.leetmodel.common.messaging.internal.OutboxRetryPolicy;
import com.leetmodel.common.messaging.internal.RocketMqMessagePublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * `common-messaging` Spring Boot 自动配置。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnClass(RocketMQTemplate.class)
public class MessagingAutoConfiguration {

    /**
     * 仅在显式启用后装配可靠消息组件。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "leetmodel.messaging", name = "enabled", havingValue = "true")
    @ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
    public static class EnabledMessagingConfiguration {

        /**
         * 提供可在测试中替换的 UTC 时间源。
         *
         * @return UTC 时间源
         */
        @Bean
        @ConditionalOnMissingBean
        public Clock messagingClock() {
            return Clock.systemUTC();
        }

        /**
         * 创建物理资源命名空间。
         *
         * @param properties 消息配置
         * @return 命名空间
         */
        @Bean
        public MessagingNamespace messagingNamespace(MessagingProperties properties) {
            return new MessagingNamespace(properties.getNamespace());
        }

        /**
         * 创建带大小上限的编解码器。
         *
         * @param objectMapper JSON 映射器
         * @param properties 消息配置
         * @return 消息编解码器
         */
        @Bean
        public MessageCodec messageCodec(ObjectMapper objectMapper, MessagingProperties properties) {
            return new MessageCodec(objectMapper, properties.getMaxPayloadBytes());
        }

        /**
         * 创建当前服务的信封工厂。
         *
         * @param applicationName Spring 应用名
         * @param clock 时间源
         * @return 信封工厂
         */
        @Bean
        public MessageEnvelopeFactory messageEnvelopeFactory(
                @Value("${spring.application.name}") String applicationName,
                Clock clock
        ) {
            return new MessageEnvelopeFactory(applicationName, clock);
        }

        /**
         * 创建事务 Outbox。
         *
         * @param jdbcTemplate 本地数据库访问
         * @param codec 消息编解码器
         * @param namespace 资源命名空间
         * @param clock 时间源
         * @return Outbox 实现
         */
        @Bean
        public JdbcMessageOutbox jdbcMessageOutbox(
                JdbcTemplate jdbcTemplate,
                MessageCodec codec,
                MessagingNamespace namespace,
                Clock clock
        ) {
            return new JdbcMessageOutbox(jdbcTemplate, codec, namespace, clock);
        }

        /**
         * 将 JDBC 实现暴露为业务 Outbox 端口。
         *
         * @param outbox JDBC Outbox
         * @return Outbox 端口
         */
        @Bean
        @ConditionalOnMissingBean(MessageOutbox.class)
        public MessageOutbox messageOutbox(JdbcMessageOutbox outbox) {
            return outbox;
        }

        /**
         * 创建事务 Inbox 基础实现。
         *
         * @param jdbcTemplate 本地数据库访问
         * @param transactionManager 本地事务管理器
         * @param namespace 资源命名空间
         * @param clock 时间源
         * @return JDBC Inbox
         */
        @Bean
        public JdbcMessageInbox jdbcMessageInbox(
                JdbcTemplate jdbcTemplate,
                PlatformTransactionManager transactionManager,
                MessagingNamespace namespace,
                Clock clock
        ) {
            return new JdbcMessageInbox(
                    jdbcTemplate,
                    new TransactionTemplate(transactionManager),
                    namespace,
                    clock
            );
        }

        /**
         * 创建低基数消息指标。
         *
         * @param registryProvider 可选指标注册表
         * @param outbox JDBC Outbox
         * @return 消息指标
         */
        @Bean
        public MessagingMetrics messagingMetrics(
                ObjectProvider<MeterRegistry> registryProvider,
                JdbcMessageOutbox outbox
        ) {
            return new MessagingMetrics(registryProvider.getIfAvailable(), outbox);
        }

        /**
         * 暴露带指标的 Inbox 端口。
         *
         * @param inbox JDBC Inbox
         * @param metrics 消息指标
         * @return Inbox 端口
         */
        @Bean
        @ConditionalOnMissingBean(MessageInbox.class)
        public MessageInbox messageInbox(JdbcMessageInbox inbox, MessagingMetrics metrics) {
            return new ObservedMessageInbox(inbox, metrics);
        }

        /**
         * 创建 RocketMQ 传输适配器；测试可提供替代发布器。
         *
         * @param rocketMQTemplate RocketMQ 模板
         * @param properties 消息配置
         * @return 消息发布器
         */
        @Bean
        @ConditionalOnBean(RocketMQTemplate.class)
        @ConditionalOnMissingBean(MessagePublisher.class)
        public MessagePublisher rocketMqMessagePublisher(
                RocketMQTemplate rocketMQTemplate,
                MessagingProperties properties
        ) {
            return new RocketMqMessagePublisher(rocketMQTemplate, properties.getSendTimeoutMs());
        }

        /**
         * 创建退避策略。
         *
         * @return Outbox 退避策略
         */
        @Bean
        public OutboxRetryPolicy outboxRetryPolicy() {
            return new OutboxRetryPolicy();
        }

        /**
         * 显式启用 Relay 且存在发布器时创建调度器。
         *
         * @param outbox JDBC Outbox
         * @param publisher 消息发布器
         * @param retryPolicy 退避策略
         * @param metrics 消息指标
         * @param clock 时间源
         * @param applicationName 应用名
         * @param properties 消息配置
         * @return Outbox Relay
         */
        @Bean
        @ConditionalOnBean(MessagePublisher.class)
        @ConditionalOnProperty(
                prefix = "leetmodel.messaging.relay",
                name = "enabled",
                havingValue = "true",
                matchIfMissing = true
        )
        public OutboxRelay outboxRelay(
                JdbcMessageOutbox outbox,
                MessagePublisher publisher,
                OutboxRetryPolicy retryPolicy,
                MessagingMetrics metrics,
                Clock clock,
                @Value("${spring.application.name}") String applicationName,
                MessagingProperties properties
        ) {
            MessagingProperties.Relay relay = properties.getRelay();
            String owner = applicationName + ":" + UUID.randomUUID();
            log.info("可靠消息已启用: namespace={}, relayBatch={}, leaseSeconds={}, maxPayloadBytes={}",
                    properties.getNamespace(), relay.getBatchSize(), relay.getLeaseSeconds(),
                    properties.getMaxPayloadBytes());
            return new OutboxRelay(
                    outbox,
                    publisher,
                    retryPolicy,
                    metrics,
                    clock,
                    owner,
                    relay.getBatchSize(),
                    Duration.ofSeconds(relay.getLeaseSeconds())
            );
        }

        /**
         * 暴露本地 Outbox 健康状态。
         *
         * @param outbox JDBC Outbox
         * @return 健康检查
         */
        @Bean(name = "messagingHealthIndicator")
        public HealthIndicator messagingHealthIndicator(JdbcMessageOutbox outbox) {
            return new MessagingHealthIndicator(outbox);
        }
    }
}
