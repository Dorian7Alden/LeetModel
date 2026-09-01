package com.leetmodel.review.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.ReviewTaskReadyPayload;
import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.review.service.ReviewService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewTaskReadyConsumerTest {

    private final MessageCodec codec = new MessageCodec(
            new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);

    @Test
    void createDomainTaskInsideInboxAction() {
        MessageInbox inbox = mock(MessageInbox.class);
        ReviewService reviewService = mock(ReviewService.class);
        when(inbox.executeOnce(eq(ReviewTaskReadyConsumer.CONSUMER_GROUP), any(), any()))
                .thenAnswer(invocation -> {
                    invocation.<Runnable>getArgument(2).run();
                    return InboxResult.CONSUMED;
                });
        ReviewTaskReadyConsumer consumer = new ReviewTaskReadyConsumer(codec, inbox, reviewService);

        consumer.onMessage(codec.encode(envelope("submission-service")));

        verify(reviewService).createTask(11L, 12L, 13L, "EVIDENCE_REVIEW_V2", "trace-review-11");
    }

    @Test
    void rejectUntrustedProducerBeforeInboxWrite() {
        MessageInbox inbox = mock(MessageInbox.class);
        ReviewService reviewService = mock(ReviewService.class);
        ReviewTaskReadyConsumer consumer = new ReviewTaskReadyConsumer(codec, inbox, reviewService);

        assertThatThrownBy(() -> consumer.onMessage(codec.encode(envelope("unknown-service"))))
                .isInstanceOf(MessageContractException.class);
        verify(inbox, never()).executeOnce(any(), any(), any());
    }

    private MessageEnvelopeV1<ReviewTaskReadyPayload> envelope(String source) {
        return new MessageEnvelopeV1<>(UUID.randomUUID().toString(),
                ReviewTaskReadyConsumer.EVENT_TYPE, 1, source, "submission", "11",
                "review:11:EVIDENCE_REVIEW_V2", Instant.now(), "trace-review-11",
                new ReviewTaskReadyPayload(11L, 12L, 13L, "EVIDENCE_REVIEW_V2"));
    }
}
