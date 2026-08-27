package com.leetmodel.review.service;

import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.mapper.ReviewTaskLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewTaskLogServiceTest {

    @Mock
    private ReviewTaskLogMapper mapper;

    @Test
    void transientExperimentStepsDoNotPolluteFormalTaskLog() {
        ReviewTask task = new ReviewTask();
        task.setWorkflowVersion("BASIC_REVIEW_V1");
        task.setAttemptNo(1);
        ReviewTaskLogService service = new ReviewTaskLogService(mapper);

        var log = service.start(task, "FETCH_PDF", "获取 PDF", "submissionId=1");
        service.succeed(log, "pdfBytes=10", null);

        assertThat(log.getStatus()).isEqualTo("SUCCEEDED");
        verify(mapper, never()).insert(log);
        verify(mapper, never()).updateById(log);
    }
}
