package com.leetmodel.ranking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.FinalSubmissionChangedPayload;
import com.leetmodel.common.api.dto.ReviewCompletedPayload;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.internal.JdbcMessageInbox;
import com.leetmodel.common.messaging.internal.RocketMqMessagePublisher;
import com.leetmodel.ranking.service.RankingRebuildRequestService;
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
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@EnabledIfEnvironmentVariable(named = "RUN_ROCKETMQ_INTEGRATION", matches = "true")
class RankingEventProtocolIntegrationTest {
    private static final String NAME_SERVER = "127.0.0.1:9876";
    private static final String SUBMISSION_TOPIC = "lm-dev%submission-event-v1";
    private static final String REVIEW_TOPIC = "lm-dev%review-event-v1";

    @Test
    @Timeout(45)
    void twoTopicsInAnyOrderAndDuplicateDeliveryConvergeToTwoMergedRequests() throws Exception {
        String suffix = UUID.randomUUID().toString();
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        JdbcMessageInbox inbox = inbox(suffix);
        RankingRebuildRequestService requestService = mock(RankingRebuildRequestService.class);
        FinalSubmissionChangedConsumer finalConsumer =
                new FinalSubmissionChangedConsumer(codec, inbox, requestService);
        ReviewCompletedConsumer reviewConsumer = new ReviewCompletedConsumer(codec, inbox, requestService);
        CountDownLatch deliveries = new CountDownLatch(4);
        PendingMessage review = pending(reviewEnvelope(), REVIEW_TOPIC, codec);
        PendingMessage submission = pending(submissionEnvelope(), SUBMISSION_TOPIC, codec);

        DefaultMQPushConsumer submissionMq = consumer(
                "lm-dev%cg-ranking-submission-v1", "mq3-submission-" + suffix,
                SUBMISSION_TOPIC, FinalSubmissionChangedConsumer.EVENT_TYPE,
                submission.eventId(), codec, body -> finalConsumer.onMessage(body), deliveries);
        DefaultMQPushConsumer reviewMq = consumer(
                "lm-dev%cg-ranking-review-v1", "mq3-review-" + suffix,
                REVIEW_TOPIC, ReviewCompletedConsumer.EVENT_TYPE,
                review.eventId(), codec, body -> reviewConsumer.onMessage(body), deliveries);
        RocketMQTemplate template = template(suffix);
        MessagePublisher publisher = new RocketMqMessagePublisher(template, 3000);
        try {
            submissionMq.start();
            reviewMq.start();
            template.afterPropertiesSet();
            TimeUnit.SECONDS.sleep(2);
            publisher.publish(review);
            publisher.publish(submission);
            publisher.publish(review);
            publisher.publish(submission);

            assertThat(deliveries.await(20, TimeUnit.SECONDS)).isTrue();
            verify(requestService, timeout(5000).times(2)).request(org.mockito.ArgumentMatchers.eq(51L), anyString());
        } finally {
            template.destroy();
            submissionMq.shutdown();
            reviewMq.shutdown();
        }
    }

    private DefaultMQPushConsumer consumer(
            String group,
            String instance,
            String topic,
            String tag,
            String expectedEventId,
            MessageCodec codec,
            java.util.function.Consumer<byte[]> action,
            CountDownLatch deliveries
    ) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(NAME_SERVER);
        consumer.setInstanceName(instance);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(1);
        consumer.setMaxReconsumeTimes(5);
        consumer.subscribe(topic, tag);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            byte[] body = messages.get(0).getBody();
            if (expectedEventId.equals(codec.decode(body, Object.class).eventId())) {
                action.accept(body);
                deliveries.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return consumer;
    }

    private RocketMQTemplate template(String suffix) {
        RocketMQTemplate template = new RocketMQTemplate();
        org.apache.rocketmq.client.producer.DefaultMQProducer producer =
                new org.apache.rocketmq.client.producer.DefaultMQProducer("mq3-ranking-producer-" + suffix);
        producer.setNamesrvAddr(NAME_SERVER);
        producer.setInstanceName("mq3-ranking-producer-instance-" + suffix);
        template.setProducer(producer);
        return template;
    }

    private JdbcMessageInbox inbox(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mq3-ranking-" + suffix
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

    private MessageEnvelopeV1<FinalSubmissionChangedPayload> submissionEnvelope() {
        return new MessageEnvelopeV1<>(UUID.randomUUID().toString(),
                FinalSubmissionChangedConsumer.EVENT_TYPE, 1, "submission-service",
                "submission-lock", "41", "final-submission:41:31", Instant.now(),
                UUID.randomUUID().toString(),
                new FinalSubmissionChangedPayload(41L, 51L, 31L, LocalDateTime.now()));
    }

    private MessageEnvelopeV1<ReviewCompletedPayload> reviewEnvelope() {
        return new MessageEnvelopeV1<>(UUID.randomUUID().toString(),
                ReviewCompletedConsumer.EVENT_TYPE, 1, "ai-review-service",
                "review-task", "21", "review-completed:21", Instant.now(),
                UUID.randomUUID().toString(),
                new ReviewCompletedPayload(
                        21L, 31L, 41L, 51L, "EVIDENCE_REVIEW_V2", LocalDateTime.now()));
    }

    private PendingMessage pending(MessageEnvelopeV1<?> envelope, String topic, MessageCodec codec) {
        return new PendingMessage(envelope.eventId(), topic, envelope.eventType(), envelope.eventId(),
                envelope.eventType(), new String(codec.encode(envelope), StandardCharsets.UTF_8),
                0, envelope.occurredAt());
    }
}
