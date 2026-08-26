package com.leetmodel.review.service;

import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.review.entity.ReviewTask;
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
import static org.mockito.ArgumentMatchers.any;
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
}
