package com.leetmodel.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_AUDIT_ROCKETMQ_INTEGRATION", matches = "true")
class OperationAuditRocketMqIntegrationTest {

    @Test
    @Timeout(60)
    void shouldEnforceAclDeliverStrictEnvelopeAndRouteExhaustedRetryToDlq() throws Exception {
        String nameserver = required("AUDIT_ROCKETMQ_NAMESERVER");
        String topic = required("AUDIT_ROCKETMQ_TOPIC");
        String group = required("AUDIT_ROCKETMQ_GROUP");
        String dlqKey = required("AUDIT_ROCKETMQ_DLQ_KEY");
        String producerUser = required("AUDIT_ROCKETMQ_PRODUCER_USER");
        String producerSecret = required("AUDIT_ROCKETMQ_PRODUCER_SECRET");
        String archiveUser = required("AUDIT_ROCKETMQ_ARCHIVE_USER");
        String archiveSecret = required("AUDIT_ROCKETMQ_ARCHIVE_SECRET");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OperationAuditMessageCodec codec = new OperationAuditMessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                MessageCodec.MAX_PAYLOAD_BYTES
        );
        CountDownLatch archived = new CountDownLatch(1);
        CountDownLatch retried = new CountDownLatch(1);
        DefaultMQPushConsumer consumer = consumer(
                group, nameserver, archiveUser, archiveSecret, suffix, codec, dlqKey, archived, retried);
        DefaultMQProducer producer = producer("audit-producer-" + suffix, nameserver, producerUser, producerSecret);

        try {
            consumer.start();
            producer.start();
            TimeUnit.SECONDS.sleep(2);

            MessageEnvelopeV1<OperationAuditPayloadV1> envelope = codec.envelope(payload());
            Message valid = message(topic, envelope.eventId(), codec.encode(envelope));
            SendResult validResult = producer.send(valid, 5_000);
            SendResult retryResult = producer.send(message(topic, dlqKey, codec.encode(envelope)), 5_000);

            assertThat(validResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
            assertThat(retryResult.getSendStatus()).isEqualTo(SendStatus.SEND_OK);
            assertThat(archived.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(retried.await(30, TimeUnit.SECONDS)).isTrue();
            assertDeniedPublish(topic, nameserver, archiveUser, archiveSecret,
                    "archive-denied-" + suffix, "no permission");
            assertDeniedPublish(topic, nameserver, null, null,
                    "anonymous-denied-" + suffix, "username");
        } finally {
            producer.shutdown();
            consumer.shutdown();
        }
    }

    private DefaultMQPushConsumer consumer(
            String group,
            String nameserver,
            String user,
            String secret,
            String suffix,
            OperationAuditMessageCodec codec,
            String dlqKey,
            CountDownLatch archived,
            CountDownLatch retried
    ) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group, hook(user, secret));
        consumer.setNamesrvAddr(nameserver);
        consumer.setInstanceName("audit-archive-" + suffix);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(1);
        consumer.setConsumeThreadMax(2);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setMaxReconsumeTimes(1);
        consumer.subscribe(required("AUDIT_ROCKETMQ_TOPIC"), OperationAuditResources.TAG);
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            org.apache.rocketmq.common.message.MessageExt message = messages.get(0);
            if (dlqKey.equals(message.getKeys())) {
                if (message.getReconsumeTimes() >= 1) retried.countDown();
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            MessageEnvelopeV1<OperationAuditPayloadV1> decoded = codec.decode(message.getBody());
            if (decoded.eventId().equals(decoded.payload().auditEventId())
                    && OperationAuditResources.TAG.equals(message.getTags())) {
                archived.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        return consumer;
    }

    private DefaultMQProducer producer(String group, String nameserver, String user, String secret) {
        DefaultMQProducer producer = user == null
                ? new DefaultMQProducer(group)
                : new DefaultMQProducer(group, hook(user, secret));
        producer.setNamesrvAddr(nameserver);
        producer.setInstanceName(group);
        producer.setSendMsgTimeout(5_000);
        return producer;
    }

    private void assertDeniedPublish(
            String topic,
            String nameserver,
            String user,
            String secret,
            String group,
            String expectedRootMessage
    ) throws Exception {
        DefaultMQProducer denied = producer(group, nameserver, user, secret);
        try {
            denied.start();
            assertThatThrownBy(() -> denied.send(
                    new Message(topic, OperationAuditResources.TAG, "denied".getBytes()), 5_000))
                    .rootCause()
                    .hasMessageContaining(expectedRootMessage);
        } finally {
            denied.shutdown();
        }
    }

    private Message message(String topic, String key, byte[] body) {
        Message message = new Message(topic, OperationAuditResources.TAG, body);
        message.setKeys(key);
        return message;
    }

    private AclClientRPCHook hook(String user, String secret) {
        return new AclClientRPCHook(new SessionCredentials(user, secret));
    }

    private OperationAuditPayloadV1 payload() {
        return new OperationAuditPayloadV1(
                1,
                "00000000-0000-4000-8000-000000000001",
                "operation-1",
                "COMPLETED",
                Instant.now(),
                "user-service",
                "1.0.0",
                "USER_RBAC",
                "USER.ROLE_CHANGE",
                "HIGH",
                "SUCCEEDED",
                "approved-change",
                null,
                "ADMIN",
                "admin-1",
                List.of("ROLE_ADMIN"),
                "USER",
                "user-1",
                "version-2",
                Map.of("roleCount", "1"),
                Map.of("roleCount", "2"),
                "trace-audit-1",
                null,
                "request-audit-1",
                null,
                null,
                null,
                null
        );
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
