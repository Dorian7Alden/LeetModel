package com.leetmodel.submission.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.ReviewTaskReadyPayload;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.PendingMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeignReviewTaskPublisherTest {

    @Test
    void deliverSameOutboxPayloadThroughIdempotentFeignEndpoint() {
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        ReviewFeignClient client = mock(ReviewFeignClient.class);
        when(client.createVersionedTask(101L, 201L, 301L, "EVIDENCE_REVIEW_V2"))
                .thenReturn(Result.ok(901L));
        FeignReviewTaskPublisher publisher = new FeignReviewTaskPublisher(codec, client);
        MessageEnvelopeV1<ReviewTaskReadyPayload> envelope = new MessageEnvelopeV1<>(
                UUID.randomUUID().toString(), ReviewTaskMessageContract.EVENT_TYPE, 1,
                "submission-service", "submission", "101",
                ReviewTaskMessageContract.idempotencyKey(101L, "EVIDENCE_REVIEW_V2"),
                Instant.now(), UUID.randomUUID().toString(),
                new ReviewTaskReadyPayload(101L, 201L, 301L, "EVIDENCE_REVIEW_V2"));
        String json = new String(codec.encode(envelope), StandardCharsets.UTF_8);

        var receipt = publisher.publish(new PendingMessage(
                envelope.eventId(), "lm-dev%review-task-v1", envelope.eventType(),
                envelope.eventId(), envelope.eventType(), json, 0, envelope.occurredAt()));

        assertThat(receipt.brokerMessageId()).isEqualTo("feign:901");
        verify(client).createVersionedTask(101L, 201L, 301L, "EVIDENCE_REVIEW_V2");
    }
}
