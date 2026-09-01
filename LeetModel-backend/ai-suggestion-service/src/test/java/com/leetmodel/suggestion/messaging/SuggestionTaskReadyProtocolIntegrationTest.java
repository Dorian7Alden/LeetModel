package com.leetmodel.suggestion.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.SuggestionTaskReadyPayload;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.common.messaging.internal.RocketMqMessagePublisher;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.service.SuggestionTaskWorkerCoordinator;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "RUN_ROCKETMQ_INTEGRATION", matches = "true")
class SuggestionTaskReadyProtocolIntegrationTest {
    private static final String NAME_SERVER = "127.0.0.1:9876";
    private static final String TOPIC = "lm-dev%suggestion-task-v1";

    @Test
    @Timeout(40)
    void duplicateBrokerDeliveryPersistsOneInboxActionAndReissuesWakeup() throws Exception {
        String suffix = UUID.randomUUID().toString();
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        JdbcMessageInbox inbox = inbox(suffix);
        SuggestionTaskMapper mapper = mock(SuggestionTaskMapper.class);
        SuggestionTaskWorkerCoordinator coordinator = mock(SuggestionTaskWorkerCoordinator.class);
        SuggestionTaskReadyConsumer domainConsumer = new SuggestionTaskReadyConsumer(
                codec, inbox, mapper, coordinator);
        long taskId = 10_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        // 使用预创建的验收消费组；本地 Broker 禁止自动创建订阅组。
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(
                "lm-dev%cg-ai-suggestion-task-v1");
        consumer.setNamesrvAddr(NAME_SERVER);
        consumer.setInstanceName("mq4-suggestion-" + suffix);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.setMaxReconsumeTimes(5);
        consumer.subscribe(TOPIC, SuggestionTaskMessageContract.EVENT_TYPE);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            domainConsumer.onMessage(messages.get(0).getBody());
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        RocketMQTemplate template = template(suffix);
        try {
            consumer.start();
            template.afterPropertiesSet();
            assertThat(waitForAssignment(consumer, 15)).isTrue();
            // 先让固定验收消费组排空上一次进程退出前未提交的 offset，再只断言本次随机任务。
            TimeUnit.SECONDS.sleep(2);
            reset(mapper, coordinator);
            MessageEnvelopeV1<SuggestionTaskReadyPayload> envelope = envelope(taskId);
            PendingMessage pending = new PendingMessage(envelope.eventId(), TOPIC,
                    envelope.eventType(), envelope.eventId(), envelope.eventType(),
                    new String(codec.encode(envelope), StandardCharsets.UTF_8), 0, envelope.occurredAt());
            MessagePublisher publisher = new RocketMqMessagePublisher(template, 3000);
            publisher.publish(pending);
            publisher.publish(pending);

            verify(mapper, timeout(20000).times(1)).markWakeup(eq(taskId), eq(101L),
                    eq("GROUNDED_SUGGESTION_V2"), any());
            verify(coordinator, timeout(20000).times(2)).wakeup(taskId);
        } finally {
            template.destroy();
            consumer.shutdown();
        }
    }

    private boolean waitForAssignment(DefaultMQPushConsumer consumer, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (!consumer.getDefaultMQPushConsumerImpl().getRebalanceImpl()
                    .getProcessQueueTable().isEmpty()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        return false;
    }

    private MessageEnvelopeV1<SuggestionTaskReadyPayload> envelope(long taskId) {
        return new MessageEnvelopeV1<>(UUID.randomUUID().toString(),
                SuggestionTaskMessageContract.EVENT_TYPE, 1, "ai-suggestion-service",
                "suggestion-task", Long.toString(taskId),
                "suggestion:" + taskId + ":attempt:1:wakeup:0",
                Instant.now(), UUID.randomUUID().toString(),
                new SuggestionTaskReadyPayload(taskId, 101L, "GROUNDED_SUGGESTION_V2"));
    }

    private RocketMQTemplate template(String suffix) {
        RocketMQTemplate template = new RocketMQTemplate();
        var producer = new org.apache.rocketmq.client.producer.DefaultMQProducer(
                "mq4-suggestion-producer-" + suffix);
        producer.setNamesrvAddr(NAME_SERVER);
        producer.setInstanceName("mq4-suggestion-producer-instance-" + suffix);
        template.setProducer(producer);
        return template;
    }

    private JdbcMessageInbox inbox(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mq4-suggestion-" + suffix
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_inbox (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, consumer_group VARCHAR(255) NOT NULL,
                  event_id VARCHAR(36) NOT NULL, event_type VARCHAR(100) NOT NULL,
                  source_service VARCHAR(100) NOT NULL, trace_id VARCHAR(100) NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  occurred_at TIMESTAMP NOT NULL, consumed_at TIMESTAMP,
                  create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL,
                  UNIQUE(consumer_group, event_id))
                """);
        return new JdbcMessageInbox(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new MessagingNamespace("lm-dev"), Clock.systemUTC());
    }
}
