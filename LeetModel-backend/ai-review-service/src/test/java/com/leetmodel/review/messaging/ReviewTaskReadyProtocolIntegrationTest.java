package com.leetmodel.review.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.ReviewTaskReadyPayload;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.common.messaging.internal.RocketMqMessagePublisher;
import com.leetmodel.review.service.ReviewService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "RUN_ROCKETMQ_INTEGRATION", matches = "true")
class ReviewTaskReadyProtocolIntegrationTest {

    private static final String NAME_SERVER = "127.0.0.1:9876";
    private static final String TOPIC = "lm-dev%review-task-v1";
    private static final String GROUP = "lm-dev%cg-ai-review-task-v1";

    @Test
    @Timeout(45)
    void duplicateBrokerDeliveryCreatesOneReviewTaskThroughInbox() throws Exception {
        String suffix = UUID.randomUUID().toString();
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        JdbcMessageInbox inbox = inbox(suffix);
        ReviewService reviewService = mock(ReviewService.class);
        ReviewTaskReadyConsumer businessConsumer = new ReviewTaskReadyConsumer(codec, inbox, reviewService);
        MessageEnvelopeV1<ReviewTaskReadyPayload> envelope = envelope();
        CountDownLatch deliveries = new CountDownLatch(2);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(GROUP);
        consumer.setNamesrvAddr(NAME_SERVER);
        consumer.setInstanceName("mq2-review-consumer-" + suffix);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(2);
        consumer.setMaxReconsumeTimes(5);
        consumer.subscribe(TOPIC, ReviewTaskReadyConsumer.EVENT_TYPE);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            businessConsumer.onMessage(messages.get(0).getBody());
            deliveries.countDown();
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        RocketMQTemplate template = new RocketMQTemplate();
        org.apache.rocketmq.client.producer.DefaultMQProducer producer =
                new org.apache.rocketmq.client.producer.DefaultMQProducer("mq2-review-producer-" + suffix);
        producer.setNamesrvAddr(NAME_SERVER);
        producer.setInstanceName("mq2-review-producer-instance-" + suffix);
        template.setProducer(producer);
        MessagePublisher publisher = new RocketMqMessagePublisher(template, 3000);
        try {
            consumer.start();
            template.afterPropertiesSet();
            TimeUnit.SECONDS.sleep(2);
            PendingMessage pending = pending(envelope, codec);
            publisher.publish(pending);
            publisher.publish(pending);

            assertThat(deliveries.await(20, TimeUnit.SECONDS)).isTrue();
            verify(reviewService, timeout(5000).times(1))
                    .createTask(101L, 201L, 301L, "EVIDENCE_REVIEW_V2", envelope.traceId());
        } finally {
            template.destroy();
            consumer.shutdown();
        }
    }

    private JdbcMessageInbox inbox(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mq2-review-" + suffix
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE message_inbox (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, consumer_group VARCHAR(255) NOT NULL,
                  event_id VARCHAR(36) NOT NULL, event_type VARCHAR(100) NOT NULL,
                  source_service VARCHAR(100) NOT NULL, trace_id VARCHAR(100) NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  occurred_at TIMESTAMP NOT NULL, consumed_at TIMESTAMP,
                  create_time TIMESTAMP NOT NULL, update_time TIMESTAMP NOT NULL,
                  UNIQUE(consumer_group, event_id))
                """);
        return new JdbcMessageInbox(jdbcTemplate,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new MessagingNamespace("lm-dev"), Clock.systemUTC());
    }

    private MessageEnvelopeV1<ReviewTaskReadyPayload> envelope() {
        return new MessageEnvelopeV1<>(UUID.randomUUID().toString(),
                ReviewTaskReadyConsumer.EVENT_TYPE, 1, "submission-service", "submission", "101",
                "review:101:EVIDENCE_REVIEW_V2", Instant.now(), UUID.randomUUID().toString(),
                new ReviewTaskReadyPayload(101L, 201L, 301L, "EVIDENCE_REVIEW_V2"));
    }

    private PendingMessage pending(
            MessageEnvelopeV1<ReviewTaskReadyPayload> envelope,
            MessageCodec codec
    ) {
        return new PendingMessage(envelope.eventId(), TOPIC, envelope.eventType(), envelope.eventId(),
                envelope.eventType(), new String(codec.encode(envelope), StandardCharsets.UTF_8),
                0, envelope.occurredAt());
    }
}
