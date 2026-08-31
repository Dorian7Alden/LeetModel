package com.leetmodel.review.service;

import com.leetmodel.review.config.ReviewWorkerProperties;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewTaskWorkerCoordinatorTest {

    @Mock ReviewTaskMapper taskMapper;
    @Mock ReviewTaskWorker worker;
    @Mock ThreadPoolTaskExecutor executor;
    private ReviewTaskWorkerCoordinator coordinator;

    @BeforeEach
    void setUp() {
        ReviewWorkerProperties properties = new ReviewWorkerProperties();
        properties.setConcurrency(2);
        properties.setLeaseSeconds(120);
        coordinator = new ReviewTaskWorkerCoordinator(taskMapper, worker, properties, executor);
    }

    @Test
    void claimOnlyAfterExecutionPermitAndRunWithLeaseToken() {
        ReviewTask candidate = new ReviewTask();
        candidate.setId(9L);
        when(taskMapper.selectNextClaimable(any())).thenReturn(candidate, (ReviewTask) null);
        when(taskMapper.claim(eq(9L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        coordinator.poll();

        verify(worker).execute(eq(9L), anyString(), anyString());
    }

    @Test
    void heartbeatRenewsEveryOwnedRunningLease() {
        ReviewTask candidate = new ReviewTask();
        candidate.setId(9L);
        when(taskMapper.selectNextClaimable(any())).thenReturn(candidate, (ReviewTask) null);
        when(taskMapper.claim(eq(9L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> null).when(executor).execute(any(Runnable.class));
        coordinator.poll();

        coordinator.heartbeat();

        verify(taskMapper).heartbeat(eq(9L), anyString(), anyString(), any(), any());
    }
}
