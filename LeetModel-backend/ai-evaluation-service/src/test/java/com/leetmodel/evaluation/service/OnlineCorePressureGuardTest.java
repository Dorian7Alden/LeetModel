package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.config.EvaluationWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnlineCorePressureGuardTest {
    private AiGatewayFeignClient gateway;
    private OnlineCorePressureGuard guard;

    @BeforeEach
    void setUp() {
        gateway = mock(AiGatewayFeignClient.class);
        EvaluationWorkerProperties properties = new EvaluationWorkerProperties();
        properties.setOnlineWarningCount(2);
        properties.setOnlineWarningWaitMs(30_000L);
        properties.setPressureCacheMs(0L);
        guard = new OnlineCorePressureGuard(gateway, properties);
    }

    @Test
    void pausesWhenP0AndP1ReachCombinedWaterline() {
        when(gateway.listQueueTasks(argThat(query -> priority(query, "P0"))))
                .thenReturn(Result.ok(List.of(task(10L))));
        when(gateway.listQueueTasks(argThat(query -> priority(query, "P1"))))
                .thenReturn(Result.ok(List.of(task(20L))));

        assertThat(guard.shouldPauseBatch()).isTrue();
        assertThat(guard.reason()).contains("count=2");
    }

    @Test
    void failsClosedWhenOnlineWaterlineCannotBeRead() {
        when(gateway.listQueueTasks(argThat(query -> priority(query, "P0"))))
                .thenThrow(new IllegalStateException("gateway unavailable"));

        assertThat(guard.shouldPauseBatch()).isTrue();
        assertThat(guard.reason()).contains("不可用");
    }

    @Test
    void allowsBatchClaimBelowCountAndWaitWaterlines() {
        when(gateway.listQueueTasks(argThat(query -> priority(query, "P0"))))
                .thenReturn(Result.ok(List.of(task(100L))));
        when(gateway.listQueueTasks(argThat(query -> priority(query, "P1"))))
                .thenReturn(Result.ok(List.of()));

        assertThat(guard.shouldPauseBatch()).isFalse();
        assertThat(guard.reason()).isNull();
    }

    private boolean priority(AiQueueQueryDTO query, String priority) {
        return query != null && priority.equals(query.getPriority()) && "QUEUED".equals(query.getState());
    }

    private AiQueueTaskDTO task(long waitMs) {
        AiQueueTaskDTO task = new AiQueueTaskDTO();
        task.setWaitMs(waitMs);
        return task;
    }
}
