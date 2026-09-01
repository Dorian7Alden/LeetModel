package com.leetmodel.review.service;

import com.leetmodel.common.api.dto.ReviewCompletedPayload;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewV2ResultMapper;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewResultPersistenceServiceTest {
    @Mock ReviewTaskMapper taskMapper;
    @Mock ReviewV1ResultMapper resultMapper;
    @Mock ReviewV2ResultMapper v2ResultMapper;
    @Mock MessageOutbox messageOutbox;
    private ReviewResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ReviewResultPersistenceService(
                taskMapper,
                resultMapper,
                v2ResultMapper,
                new MessageEnvelopeFactory("ai-review-service",
                        Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)),
                messageOutbox);
    }

    @Test
    void resultTaskCompletionAndEventShareOneTransactionBoundary() {
        when(taskMapper.markCompleted(eq(21L), eq("token"), any())).thenReturn(1);

        service.complete(task(), submission(), result(), "token");

        ArgumentCaptor<MessageEnvelopeV1<?>> envelope = ArgumentCaptor.forClass(MessageEnvelopeV1.class);
        verify(messageOutbox).enqueue(eq("review-event-v1"), eq("REVIEW_COMPLETED"), envelope.capture());
        ReviewCompletedPayload payload = (ReviewCompletedPayload) envelope.getValue().payload();
        assertThat(payload.reviewTaskId()).isEqualTo(21L);
        assertThat(payload.problemId()).isEqualTo(51L);
        assertThat(envelope.getValue().idempotencyKey()).isEqualTo("review-completed:21");
    }

    @Test
    void lostLeaseDoesNotPublishCompletionEvent() {
        when(taskMapper.markCompleted(eq(21L), eq("stale"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.complete(task(), submission(), result(), "stale"))
                .isInstanceOf(IllegalStateException.class);

        verify(messageOutbox, never()).enqueue(any(), any(), any());
    }

    private ReviewTask task() {
        ReviewTask task = new ReviewTask();
        task.setId(21L);
        task.setSubmissionId(31L);
        task.setTeamId(41L);
        task.setProblemId(51L);
        task.setWorkflowVersion("BASIC_REVIEW_V1");
        task.setTraceId("trace-21");
        return task;
    }

    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(31L, 41L, 51L, 1, "paper.pdf");
    }

    private ReviewWorkflowResult result() {
        return new ReviewWorkflowResult(new BigDecimal("88.00"), "{}", "model", "call-1", null);
    }
}
