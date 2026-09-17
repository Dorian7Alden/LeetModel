package com.leetmodel.admin.service;

import com.leetmodel.admin.client.EvaluationMessagingFeignClient;
import com.leetmodel.admin.client.RankingMessagingFeignClient;
import com.leetmodel.admin.client.ReviewMessagingFeignClient;
import com.leetmodel.admin.client.SubmissionMessagingFeignClient;
import com.leetmodel.admin.client.SuggestionMessagingFeignClient;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.MessagingConsumerDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterRecordDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterReplayRequestDTO;
import com.leetmodel.common.api.dto.MessagingOperationResultDTO;
import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOverviewDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMessagingOperationsServiceTest {

    @Mock SubmissionMessagingFeignClient submission;
    @Mock ReviewMessagingFeignClient review;
    @Mock RankingMessagingFeignClient ranking;
    @Mock SuggestionMessagingFeignClient suggestion;
    @Mock EvaluationMessagingFeignClient evaluation;
    @Mock AiGatewayFeignClient aiGateway;
    private AdminMessagingOperationsService service;

    @BeforeEach
    void setUp() {
        service = new AdminMessagingOperationsService(
                submission, review, ranking, suggestion, evaluation, aiGateway);
    }

    @Test
    void overviewShouldKeepAvailableServicesWhenOneFails() {
        when(submission.messagingOverview()).thenReturn(Result.ok(overview("submission-service")));
        when(review.messagingOverview()).thenThrow(new IllegalStateException("down"));
        when(ranking.messagingOverview()).thenReturn(Result.ok(overview("ranking-service")));
        when(suggestion.messagingOverview()).thenReturn(Result.ok(overview("ai-suggestion-service")));
        when(evaluation.messagingOverview()).thenReturn(Result.ok(overview("ai-evaluation-service")));

        var result = service.overview();

        assertThat(result.services()).extracting(MessagingOverviewDTO::service)
                .containsExactly("submission-service", "ranking-service",
                        "ai-suggestion-service", "ai-evaluation-service");
        assertThat(result.unavailableServices()).containsExactly("ai-review-service");
    }

    @Test
    void traceShouldAssociateEventInboxAndAiCallId() {
        MessagingOutboxRecordDTO outbox = new MessagingOutboxRecordDTO(
                "submission-service", "event-1", "topic", "TAG", "EVENT", "submission",
                "1", "trace-1", "PUBLISHED", 0, null, null, null, null);
        MessagingInboxRecordDTO inbox = new MessagingInboxRecordDTO(
                "ai-review-service", "group", "event-1", "EVENT", "submission-service",
                "trace-1", "CONSUMED", null, null, null);
        AiCallLogDTO aiCall = new AiCallLogDTO();
        aiCall.setCallId("ai-call-1");
        aiCall.setTraceId("trace-1");
        when(submission.messagingOutbox(isNull(), any(), isNull(), any()))
                .thenReturn(Result.ok(List.of(outbox)));
        when(review.messagingInbox(any(), isNull(), any())).thenReturn(Result.ok(List.of(inbox)));
        when(aiGateway.listCalls(any())).thenReturn(Result.ok(List.of(aiCall)));

        var result = service.trace("trace-1");

        assertThat(result.producedEvents()).extracting(MessagingOutboxRecordDTO::eventId)
                .containsExactly("event-1");
        assertThat(result.consumedEvents()).extracting(MessagingInboxRecordDTO::eventId)
                .containsExactly("event-1");
        assertThat(result.aiCalls()).extracting(AiCallLogDTO::getCallId)
                .containsExactly("ai-call-1");
        assertThat(result.unavailableServices()).contains(
                "ranking-service", "ai-suggestion-service", "ai-evaluation-service");
    }

    @Test
    void dlqReplayShouldVerifyConsumerDlqBeforeRestoringSourceOutbox() {
        String eventId = "00000000-0000-4000-8000-000000000009";
        when(review.locateMessagingDeadLetters(any())).thenReturn(Result.ok(List.of(
                new MessagingDeadLetterRecordDTO("ai-review-service", "group", eventId,
                        "REVIEW_TASK_READY", "submission-service", "broker-1", 16, null))));
        when(submission.replayMessagingOutbox(any())).thenReturn(Result.ok(
                new MessagingOperationResultDTO("submission-service", "OUTBOX_REPLAY", 1, List.of(eventId))));

        var result = service.replayDeadLetters("ai-review-service",
                new MessagingDeadLetterReplayRequestDTO("group", List.of(eventId), "修复契约后恢复"), 7L);

        assertThat(result.operation()).isEqualTo("DLQ_REPLAY");
        assertThat(result.acceptedIds()).containsExactly(eventId);
    }

    private MessagingOverviewDTO overview(String service) {
        return new MessagingOverviewDTO(service, Map.of("PENDING", 0L), 0L, 0L,
                List.<MessagingConsumerDTO>of(), Map.of(), "MANUAL_OUTBOX_EVENT_ID_ONLY");
    }
}
