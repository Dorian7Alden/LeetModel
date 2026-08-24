package com.leetmodel.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.review.ai.ReviewModelClient;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.mapper.ReviewResultMapper;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock ReviewTaskMapper taskMapper; @Mock ReviewResultMapper resultMapper;
    @Mock SubmissionFeignClient submissionFeignClient; @Mock TeamFeignClient teamFeignClient;
    @Mock StorageService storageService; @Mock ReviewModelClient modelClient;
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createPersistentWaitingTask() {
        ReviewService service = new ReviewService(taskMapper, resultMapper, submissionFeignClient,
                teamFeignClient, storageService, modelClient, objectMapper);
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(taskMapper.insert(any(ReviewTask.class))).thenAnswer(invocation -> {
            ReviewTask task = invocation.getArgument(0); task.setId(9L); return 1;
        });
        Long id = service.createTask(3L);
        assertEquals(9L, id);
        verify(taskMapper).insert(argThat((ReviewTask task) -> "WAITING".equals(task.getStatus())
                && ReviewService.WORKFLOW_VERSION.equals(task.getWorkflowVersion())));
    }
}
