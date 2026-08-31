package com.leetmodel.ranking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.FinalSubmissionChangedPayload;
import com.leetmodel.common.api.dto.ReviewCompletedPayload;
import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.ranking.service.RankingRebuildRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingEventConsumerTest {
    @Mock MessageInbox inbox;
    @Mock RankingRebuildRequestService requestService;
    private MessageCodec codec;

    @BeforeEach
    void setUp() {
        codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return InboxResult.CONSUMED;
        }).when(inbox).executeOnce(any(), any(), any());
    }

    @Test
    void finalSubmissionEventRequestsOnlyItsProblem() {
        MessageEnvelopeV1<FinalSubmissionChangedPayload> envelope = factory("submission-service").create(
                FinalSubmissionChangedConsumer.EVENT_TYPE,
                "submission-lock", "41", "final-submission:41:31", "trace-final",
                new FinalSubmissionChangedPayload(
                        41L, 51L, 31L, LocalDateTime.of(2026, 9, 1, 8, 0)));

        new FinalSubmissionChangedConsumer(codec, inbox, requestService)
                .onMessage(codec.encode(envelope));

        verify(requestService).request(51L, "trace-final");
        verify(inbox).executeOnce(eq(FinalSubmissionChangedConsumer.CONSUMER_GROUP), any(), any());
    }

    @Test
    void reviewCompletedEventRequestsOnlyItsProblem() {
        MessageEnvelopeV1<ReviewCompletedPayload> envelope = factory("ai-review-service").create(
                ReviewCompletedConsumer.EVENT_TYPE,
                "review-task", "21", "review-completed:21", "trace-review",
                new ReviewCompletedPayload(
                        21L, 31L, 41L, 51L, "EVIDENCE_REVIEW_V2",
                        LocalDateTime.of(2026, 9, 1, 8, 5)));

        new ReviewCompletedConsumer(codec, inbox, requestService)
                .onMessage(codec.encode(envelope));

        verify(requestService).request(51L, "trace-review");
        verify(inbox).executeOnce(eq(ReviewCompletedConsumer.CONSUMER_GROUP), any(), any());
    }

    private MessageEnvelopeFactory factory(String service) {
        return new MessageEnvelopeFactory(
                service, Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
    }
}
