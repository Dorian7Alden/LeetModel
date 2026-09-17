package com.leetmodel.ranking.service;

import com.leetmodel.ranking.config.RankingRebuildProperties;
import com.leetmodel.ranking.entity.RankingRebuildTask;
import com.leetmodel.ranking.mapper.RankingRebuildTaskMapper;
import com.leetmodel.ranking.observability.RankingRebuildMetrics;
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
class RankingRebuildCoordinatorTest {
    @Mock RankingRebuildTaskMapper taskMapper;
    @Mock RankingRebuildWorker worker;
    @Mock ThreadPoolTaskExecutor executor;
    @Mock RankingRebuildMetrics metrics;
    private RankingRebuildCoordinator coordinator;

    @BeforeEach
    void setUp() {
        RankingRebuildProperties properties = new RankingRebuildProperties();
        properties.setLeaseSeconds(300);
        coordinator = new RankingRebuildCoordinator(taskMapper, worker, properties, executor, metrics);
    }

    @Test
    void claimAndHeartbeatOnlyLocallyActiveTask() {
        RankingRebuildTask candidate = new RankingRebuildTask();
        candidate.setId(9L);
        when(taskMapper.selectNextClaimable(any())).thenReturn(candidate);
        when(taskMapper.claim(eq(9L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> null).when(executor).execute(any(Runnable.class));

        coordinator.poll();
        coordinator.heartbeat();

        verify(taskMapper).heartbeat(eq(9L), anyString(), anyString(), any(), any());
    }

    @Test
    void expiredRunningLeaseIsCountedAsTakeover() {
        RankingRebuildTask candidate = new RankingRebuildTask();
        candidate.setId(9L);
        candidate.setStatus("RUNNING");
        when(taskMapper.selectNextClaimable(any())).thenReturn(candidate);
        when(taskMapper.claim(eq(9L), anyString(), anyString(), any(), any())).thenReturn(1);
        doAnswer(invocation -> null).when(executor).execute(any(Runnable.class));

        coordinator.poll();

        verify(metrics).claimed(true);
    }
}
