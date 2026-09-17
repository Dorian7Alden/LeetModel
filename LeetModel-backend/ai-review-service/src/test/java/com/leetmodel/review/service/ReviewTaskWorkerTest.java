package com.leetmodel.review.service;

import com.leetmodel.common.ai.client.AiClientException;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.config.ReviewWorkerProperties;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowRegistry;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewTaskWorkerTest {

    @Mock ReviewTaskMapper taskMapper;
    @Mock SubmissionFeignClient submissionFeignClient;
    @Mock ReviewWorkflowRegistry workflowRegistry;
    @Mock ReviewTaskLogService logService;
    @Mock ReviewResultPersistenceService persistenceService;
    @Mock ReviewWorkflow workflow;
    private ReviewTaskWorker worker;

    @BeforeEach
    void setUp() {
        ReviewWorkerProperties properties = new ReviewWorkerProperties();
        properties.setLeaseSeconds(120);
        properties.setMaxAttempts(3);
        worker = new ReviewTaskWorker(taskMapper, submissionFeignClient, workflowRegistry,
                logService, persistenceService, properties);
    }

    @Test
    void skipExternalWorkAfterLeaseWasLost() {
        ReviewTask task = task();
        when(taskMapper.selectById(21L)).thenReturn(task);
        when(taskMapper.markRunning(eq(21L), eq("owner"), eq("token"), anyString(), any(), any()))
                .thenReturn(0);

        worker.execute(21L, "owner", "token");

        verify(submissionFeignClient, never()).getForReview(anyLong());
    }

    @Test
    void persistSuccessfulResultWithFencingToken() throws Exception {
        ReviewTask task = runningTask();
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(workflowRegistry.required("EVIDENCE_REVIEW_V2")).thenReturn(workflow);
        ReviewWorkflowResult result = new ReviewWorkflowResult(
                new BigDecimal("88.00"), "{}", "model", "call-1", 91L);
        when(workflow.execute(any(), any())).thenReturn(result);
        when(logService.start(any(), any(), any(), any())).thenReturn(new ReviewTaskLog());

        worker.execute(21L, "owner", "token");

        verify(persistenceService).complete(task, submission(), result, "token");
        verify(taskMapper, never()).markTerminalFailure(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void retryDependencyFailureWithNewBusinessAttempt() {
        ReviewTask task = runningTask();
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);
        when(logService.start(any(), any(), any(), any())).thenReturn(new ReviewTaskLog());

        worker.execute(21L, "owner", "token");

        verify(taskMapper).scheduleRetry(eq(21L), eq("token"), any(),
                eq("DEPENDENCY_TRANSIENT"), anyString(),
                eq("review:31:EVIDENCE_REVIEW_V2:attempt:2"));
    }

    @Test
    void markUnknownWithoutAutomaticRetryWhenAiOutcomeIsUncertain() throws Exception {
        ReviewTask task = runningTask();
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(workflowRegistry.required("EVIDENCE_REVIEW_V2")).thenReturn(workflow);
        when(workflow.execute(any(), any())).thenThrow(new AiClientException(51213, "unknown"));
        when(logService.start(any(), any(), any(), any())).thenReturn(new ReviewTaskLog());

        worker.execute(21L, "owner", "token");

        verify(taskMapper).markTerminalFailure(21L, "token", "UNKNOWN", "AI_UNKNOWN",
                "AI 上游结果未知，禁止自动重试");
        verify(taskMapper, never()).scheduleRetry(anyLong(), anyString(), any(), anyString(), anyString(), anyString());
        verify(taskMapper, never()).scheduleSameAttempt(anyLong(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void pollPendingAiWithSameAttemptAndIdempotencyKey() throws Exception {
        ReviewTask task = runningTask();
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(workflowRegistry.required("EVIDENCE_REVIEW_V2")).thenReturn(workflow);
        when(workflow.execute(any(), any())).thenThrow(new AiClientException(51212, "pending"));
        when(logService.start(any(), any(), any(), any())).thenReturn(new ReviewTaskLog());

        worker.execute(21L, "owner", "token");

        verify(taskMapper).scheduleSameAttempt(eq(21L), eq("token"), any(), eq("AI_PENDING"), eq("pending"));
        verify(taskMapper, never()).scheduleRetry(anyLong(), anyString(), any(), anyString(), anyString(), anyString());
    }

    private ReviewTask runningTask() {
        ReviewTask task = task();
        when(taskMapper.selectById(21L)).thenReturn(task);
        when(taskMapper.markRunning(eq(21L), eq("owner"), eq("token"), anyString(), any(), any()))
                .thenReturn(1);
        return task;
    }

    private ReviewTask task() {
        ReviewTask task = new ReviewTask();
        task.setId(21L);
        task.setSubmissionId(31L);
        task.setTeamId(41L);
        task.setProblemId(51L);
        task.setWorkflowVersion("EVIDENCE_REVIEW_V2");
        task.setAttemptNo(1);
        task.setMaxAttempts(3);
        task.setTraceId("trace-21");
        return task;
    }

    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(31L, 41L, 51L, 1, "paper.pdf");
    }
}
