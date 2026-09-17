package com.leetmodel.ranking.service;

import com.leetmodel.ranking.entity.RankingRebuildTask;
import com.leetmodel.ranking.mapper.RankingRebuildTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingRebuildWorkerTest {
    @Mock RankingRebuildTaskMapper taskMapper;
    @Mock RankingService rankingService;
    private RankingRebuildWorker worker;

    @BeforeEach
    void setUp() {
        worker = new RankingRebuildWorker(taskMapper, rankingService);
    }

    @Test
    void executesExactlyTheRevisionCapturedAtClaim() {
        when(taskMapper.selectById(9L)).thenReturn(task("token"));

        worker.execute(9L, "token");

        verify(rankingService).rebuildClaimed(51L, 9L, "token", 3L);
        verify(taskMapper, never()).scheduleRetry(any(), any(), any(), anyString(), any());
    }

    @Test
    void dependencyFailureKeepsOldRankingAndSchedulesBoundedRetry() {
        when(taskMapper.selectById(9L)).thenReturn(task("token"));
        org.mockito.Mockito.doThrow(new IllegalStateException("dependency unavailable"))
                .when(rankingService).rebuildClaimed(51L, 9L, "token", 3L);

        worker.execute(9L, "token");

        verify(taskMapper).scheduleRetry(eq(9L), eq("token"), any(),
                eq("dependency unavailable"), any());
    }

    @Test
    void staleExecutionCannotStartWithAnotherToken() {
        when(taskMapper.selectById(9L)).thenReturn(task("new-token"));

        worker.execute(9L, "stale-token");

        verify(rankingService, never()).rebuildClaimed(any(), any(), any(), any());
    }

    private RankingRebuildTask task(String token) {
        RankingRebuildTask task = new RankingRebuildTask();
        task.setId(9L);
        task.setProblemId(51L);
        task.setStatus("RUNNING");
        task.setLeaseToken(token);
        task.setRunningRevision(3L);
        task.setRetryCount(0);
        return task;
    }
}
