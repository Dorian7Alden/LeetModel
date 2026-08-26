package com.leetmodel.review.service;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.enums.ReviewErrorCode;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewVersionMapper;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock ReviewTaskMapper taskMapper;
    @Mock ReviewV1ResultMapper resultMapper;
    @Mock ReviewVersionMapper versionMapper;
    @Mock SubmissionFeignClient submissionFeignClient;
    @Mock TeamFeignClient teamFeignClient;
    @Mock ReviewWorkflowRegistry workflowRegistry;
    @Mock ReviewTaskLogService logService;
    @Mock ReviewResultPersistenceService persistenceService;
    @Mock ReviewWorkflow workflow;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(taskMapper, resultMapper, versionMapper, submissionFeignClient,
                teamFeignClient, workflowRegistry, logService, persistenceService);
    }

    @Test
    void createPersistentWaitingTaskWithLockedVersionAndPrompt() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(workflowRegistry.required(ReviewService.WORKFLOW_VERSION)).thenReturn(workflow);
        when(workflow.versionCode()).thenReturn(ReviewService.WORKFLOW_VERSION);
        when(workflow.versionId()).thenReturn(1L);
        when(workflow.currentPrompt()).thenReturn("prompt snapshot");
        when(taskMapper.insert(any(ReviewTask.class))).thenAnswer(invocation -> {
            ReviewTask task = invocation.getArgument(0); task.setId(9L); return 1;
        });

        assertEquals(9L, service.createTask(3L, 4L, 5L));
        verify(taskMapper).insert(argThat((ReviewTask task) -> "WAITING".equals(task.getStatus())
                && ReviewService.WORKFLOW_VERSION.equals(task.getWorkflowVersion())
                && "prompt snapshot".equals(task.getPromptSnapshot())
                && task.getTeamId() == 4L && task.getProblemId() == 5L
                && task.getAttemptNo() == 1));
    }

    @Test
    void returnExistingTaskForSameSubmissionAndVersion() {
        ReviewTask existing = new ReviewTask(); existing.setId(12L);
        when(taskMapper.selectOne(any())).thenReturn(existing);
        assertEquals(12L, service.createTask(3L, 4L, 5L));
    }

    @Test
    void onlyOneWorkerCanClaimWaitingTask() {
        ReviewTask task = task(20L, "WAITING");
        when(taskMapper.selectNextWaiting()).thenReturn(task);
        when(taskMapper.claim(any(), any())).thenReturn(0);

        service.processNext();

        verify(submissionFeignClient, never()).getForReview(any());
        verify(workflowRegistry, never()).required(any());
    }

    @Test
    void rejectTaskQueryWhenSubmissionDependencyFails() {
        ReviewTask task = task(21L, "WAITING");
        when(taskMapper.selectById(21L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getTask(21L, 10L));

        assertEquals(ReviewErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), error.getCode());
        verify(teamFeignClient, never()).getMemberIds(any());
    }

    @Test
    void rejectNonMemberFromTaskResult() {
        ReviewTask task = task(22L, "COMPLETED");
        when(taskMapper.selectById(22L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(41L)).thenReturn(Result.ok(java.util.List.of(11L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getTask(22L, 10L));

        assertEquals(ReviewErrorCode.NOT_TEAM_MEMBER.getCode(), error.getCode());
        verify(resultMapper, never()).selectOne(any());
    }

    @Test
    void resetFailedTaskForAuthorizedRetry() {
        ReviewTask task = task(23L, "FAILED");
        task.setRetryCount(1);
        task.setAttemptNo(2);
        task.setErrorMessage("provider unavailable");
        when(taskMapper.selectById(23L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(41L)).thenReturn(Result.ok(java.util.List.of(10L)));

        var result = service.retry(23L, 10L);

        assertEquals("WAITING", result.getStatus());
        assertEquals(2, result.getRetryCount());
        assertEquals(3, result.getAttemptNo());
        verify(taskMapper).resetForRetry(argThat(value -> value.getStartedAt() == null
                && value.getFinishedAt() == null && value.getErrorMessage() == null));
    }

    @Test
    void rejectRetryForCompletedTask() {
        ReviewTask task = task(24L, "COMPLETED");
        when(taskMapper.selectById(24L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(41L)).thenReturn(Result.ok(java.util.List.of(10L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.retry(24L, 10L));

        assertEquals(ReviewErrorCode.TASK_NOT_FAILED.getCode(), error.getCode());
        verify(taskMapper, never()).resetForRetry(any());
    }

    @Test
    void markClaimedTaskFailedWhenDependencyFails() {
        ReviewTask task = task(25L, "WAITING");
        ReviewTaskLog runLog = new ReviewTaskLog();
        when(taskMapper.selectNextWaiting()).thenReturn(task);
        when(taskMapper.claim(any(), any())).thenReturn(1);
        when(logService.start(any(), any(), any(), any())).thenReturn(runLog);
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);

        service.processNext();

        verify(logService).fail(any(), any());
        verify(taskMapper).updateById(argThat((ReviewTask value) -> "FAILED".equals(value.getStatus())
                && value.getFinishedAt() != null && value.getErrorMessage() != null));
    }

    private ReviewTask task(Long id, String status) {
        ReviewTask task = new ReviewTask();
        task.setId(id);
        task.setSubmissionId(31L);
        task.setTeamId(41L);
        task.setVersionId(1L);
        task.setWorkflowVersion(ReviewService.WORKFLOW_VERSION);
        task.setStatus(status);
        task.setRetryCount(0);
        task.setAttemptNo(1);
        return task;
    }

    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(31L, 41L, 51L, 1, "submissions/41/paper.pdf");
    }
}
