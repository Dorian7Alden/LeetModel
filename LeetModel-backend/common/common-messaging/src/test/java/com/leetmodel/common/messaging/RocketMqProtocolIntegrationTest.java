package com.leetmodel.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.common.messaging.internal.MessagingMetrics;
import com.leetmodel.common.messaging.internal.ObservedMessageInbox;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_ROCKETMQ_INTEGRATION", matches = "true")
class RocketMqProtocolIntegrationTest {

    private static final String NAME_SERVER = "127.0.0.1:9876";
    private static final String TOPIC = "lm-dev%review-task-v1";
    private static final String GROUP = System.getenv().getOrDefault(
            "ROCKETMQ_INTEGRATION_GROUP", "lm-dev%cg-ai-review-task-v1");

    @Test
    @Timeout(45)
    void shouldSendConsumeDeduplicateAndRetryWithHistoricalClient() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String duplicateTag = "MQ1_DUPLICATE_" + suffix.substring(0, 8);
        String retryTag = "MQ1_RETRY_" + suffix.substring(0, 8);
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                MessageCodec.MAX_PAYLOAD_BYTES
        );
        MessageEnvelopeV1<Map<String, String>> duplicateEnvelope = envelope(UUID.randomUUID().toString());
        MessageEnvelopeV1<Map<String, String>> retryEnvelope = envelope(UUID.randomUUID().toString());
        MessageInbox inbox = inbox(suffix);
        AtomicInteger domainActions = new AtomicInteger();
        AtomicInteger observedRetryTimes = new AtomicInteger(-1);
        CountDownLatch duplicateDeliveries = new CountDownLatch(2);
        CountDownLatch retryDelivery = new CountDownLatch(1);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(GROUP);
        consumer.setNamesrvAddr(NAME_SERVER);
        consumer.setInstanceName("mq1-consumer-" + suffix);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(2);
        consumer.setMaxReconsumeTimes(5);
        consumer.subscribe(TOPIC, duplicateTag + " || " + retryTag);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> consume(
                messages.get(0),
                codec,
                inbox,
                domainActions,
                duplicateDeliveries,
                retryDelivery,
                observedRetryTimes,
                retryTag
        ));

        DefaultMQProducer producer = new DefaultMQProducer("mq1-producer-" + suffix);
        producer.setNamesrvAddr(NAME_SERVER);
        producer.setInstanceName("mq1-producer-instance-" + suffix);
        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
        rocketMQTemplate.setProducer(producer);
        MessagePublisher publisher = new com.leetmodel.common.messaging.internal.RocketMqMessagePublisher(
                rocketMQTemplate,
                3000
        );
        try {
            consumer.start();
            rocketMQTemplate.afterPropertiesSet();
            TimeUnit.SECONDS.sleep(2);

            publisher.publish(pending(duplicateTag, duplicateEnvelope, codec));
            publisher.publish(pending(duplicateTag, duplicateEnvelope, codec));
            publisher.publish(pending(retryTag, retryEnvelope, codec));

            assertThat(duplicateDeliveries.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(retryDelivery.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(domainActions).hasValue(1);
            assertThat(observedRetryTimes).hasValue(1);
            assertThat(consumer.getMaxReconsumeTimes()).isEqualTo(5);
        } finally {
            rocketMQTemplate.destroy();
            consumer.shutdown();
        }
    }

    private ConsumeConcurrentlyStatus consume(
            MessageExt message,
            MessageCodec codec,
            MessageInbox inbox,
            AtomicInteger domainActions,
            CountDownLatch duplicateDeliveries,
            CountDownLatch retryDelivery,
            AtomicInteger observedRetryTimes,
            String retryTag
    ) {
        if (retryTag.equals(message.getTags()) && message.getReconsumeTimes() == 0) {
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
        if (retryTag.equals(message.getTags())) {
            observedRetryTimes.set(message.getReconsumeTimes());
            retryDelivery.countDown();
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        MessageEnvelopeV1<?> envelope = codec.decode(message.getBody(), Map.class);
        try (CorrelationContext.Scope ignored = MessageCorrelationContext.open(envelope)) {
            inbox.executeOnce("cg-ai-review-task-v1", envelope, domainActions::incrementAndGet);
        }
        duplicateDeliveries.countDown();
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private PendingMessage pending(
            String tag,
            MessageEnvelopeV1<Map<String, String>> envelope,
            MessageCodec codec
    ) {
        return new PendingMessage(
                envelope.eventId(),
                TOPIC,
                tag,
                envelope.eventId(),
                envelope.eventType(),
                new String(codec.encode(envelope), StandardCharsets.UTF_8),
                0,
                envelope.occurredAt()
        );
    }

    private MessageEnvelopeV1<Map<String, String>> envelope(String eventId) {
        return new MessageEnvelopeV1<>(
                eventId,
                "REVIEW_TASK_READY",
                1,
                "submission-service",
                "submission",
                "submission-1",
                "review:submission-1:v1",
                Instant.now(),
                "trace-" + eventId,
                Map.of("submissionId", "submission-1")
        );
    }

    private MessageInbox inbox(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rocketmq-" + suffix
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("messaging-schema.sql")).execute(dataSource);
        JdbcMessageInbox jdbcInbox = new JdbcMessageInbox(
                new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new MessagingNamespace("lm-dev"),
                Clock.fixed(Instant.now(), ZoneOffset.UTC)
        );
        return new ObservedMessageInbox(jdbcInbox, new MessagingMetrics(null, null, jdbcInbox));
    }
}
