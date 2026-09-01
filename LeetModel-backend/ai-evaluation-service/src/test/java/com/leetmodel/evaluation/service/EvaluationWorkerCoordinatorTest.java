package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.config.EvaluationWorkerProperties;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationWorkerCoordinatorTest {
    @Mock EvaluationRunAttemptMapper runMapper;
    @Mock EvaluationService evaluationService;
    @Mock OnlineCorePressureGuard pressureGuard;
    @Mock ThreadPoolTaskExecutor executor;
    private EvaluationWorkerCoordinator coordinator;

    @BeforeEach
    void setUp() {
        EvaluationWorkerProperties properties = new EvaluationWorkerProperties();
        properties.setConcurrency(1);
        properties.setLeaseSeconds(120);
        coordinator = new EvaluationWorkerCoordinator(
                runMapper, evaluationService, properties, pressureGuard, executor);
    }

    @Test
    void clearWaterlineClaimsByRunIdAndExecutesWithFencingToken() {
        when(pressureGuard.shouldPauseBatch()).thenReturn(false);
        when(runMapper.claim(eq(301L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        coordinator.wakeup(301L);

        verify(evaluationService).executeClaimed(eq(301L), anyString());
    }

    @Test
    void onlinePressureStopsNewLeaseClaim() {
        when(pressureGuard.shouldPauseBatch()).thenReturn(true);

        coordinator.wakeup(301L);

        verify(runMapper, never()).claim(any(), any(), any(), any(), any());
    }

    @Test
    void heartbeatRenewsOnlyLocallyTrackedLease() {
        when(pressureGuard.shouldPauseBatch()).thenReturn(false);
        when(runMapper.claim(eq(301L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> null).when(executor).execute(any(Runnable.class));
        coordinator.wakeup(301L);

        coordinator.heartbeat();

        verify(runMapper).heartbeat(eq(301L), anyString(), anyString(), any(), any());
    }

    @Test
    void idleScheduledDrainDoesNotQueryOnlinePressure() {
        coordinator.drain();

        verify(pressureGuard, never()).shouldPauseBatch();
    }
}
