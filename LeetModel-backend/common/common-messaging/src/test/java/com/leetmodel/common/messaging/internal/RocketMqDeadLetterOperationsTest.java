package com.leetmodel.common.messaging.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.MessagingConsumerDTO;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqDeadLetterOperationsTest {

    @Test
    void shouldLocateOnlyExactEventInOwnedDeadLetterQueueWithoutExposingPayload() throws Exception {
        String eventId = "00000000-0000-4000-8000-000000000009";
        String group = "lm-test%cg-ai-review-task-v1";
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        MessageEnvelopeV1<Map<String, String>> envelope = new MessageEnvelopeV1<>(eventId,
                "REVIEW_TASK_READY", 1, "submission-service", "submission", "1",
                "secret-idempotency", Instant.parse("2026-09-01T00:00:00Z"), "trace-1",
                Map.of("paper", "must-not-leak"));
        MessageExt message = new MessageExt();
        message.setKeys(eventId);
        message.setMsgId("broker-message-1");
        message.setBody(codec.encode(envelope));
        message.setStoreTimestamp(Instant.parse("2026-09-01T00:01:00Z").toEpochMilli());
        message.setReconsumeTimes(16);

        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        when(producer.queryMessage(eq("%DLQ%" + group), eq(eventId), eq(8), anyLong(), anyLong()))
                .thenReturn(new QueryResult(System.currentTimeMillis(), List.of(message)));
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        when(template.getProducer()).thenReturn(producer);
        RocketMqConsumerControl consumers = mock(RocketMqConsumerControl.class);
        when(consumers.statuses()).thenReturn(List.of(
                new MessagingConsumerDTO(group, "lm-test%review-task-v1", false, true)));

        var records = new RocketMqDeadLetterOperations(
                "ai-review-service", template, codec, consumers).locate(group, List.of(eventId));

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.eventId()).isEqualTo(eventId);
            assertThat(record.sourceService()).isEqualTo("submission-service");
            assertThat(record.reconsumeTimes()).isEqualTo(16);
            assertThat(record.toString()).doesNotContain("must-not-leak").doesNotContain("secret-idempotency");
        });
    }
}
