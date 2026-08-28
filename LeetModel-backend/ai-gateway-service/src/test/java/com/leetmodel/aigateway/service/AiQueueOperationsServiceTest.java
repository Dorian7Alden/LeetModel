package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.scheduling.AiTaskWaitRegistry;
import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiQueueOperationsServiceTest {

    @Test
    void filtersByWaitAndReturnsMetadataOnly() {
        AiCallTaskMapper mapper = mock(AiCallTaskMapper.class);
        AiTaskWaitRegistry waits = mock(AiTaskWaitRegistry.class);
        AiCallTask shortWait = task("short", "QUEUED", 2);
        AiCallTask longWait = task("long", "QUEUED", 20);
        longWait.setRequestPayload("private prompt");
        longWait.setResultPayload("private answer");
        when(mapper.selectForMonitoring("QUEUED", "P3", "ai-review-service"))
                .thenReturn(List.of(shortWait, longWait));
        AiQueueQueryDTO query = new AiQueueQueryDTO();
        query.setState("QUEUED");
        query.setPriority("P3");
        query.setCallerService("ai-review-service");
        query.setMinWaitMs(10_000L);

        var result = new AiQueueOperationsService(mapper, waits).list(query);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.getTaskId()).isEqualTo("long");
            assertThat(dto.getWaitMs()).isGreaterThanOrEqualTo(10_000L);
        });
        assertThat(result.get(0).toString()).doesNotContain("private prompt", "private answer");
    }

    @Test
    void queuedCancellationUsesConditionalUpdateAndNotifiesWaiter() {
        AiCallTaskMapper mapper = mock(AiCallTaskMapper.class);
        AiTaskWaitRegistry waits = mock(AiTaskWaitRegistry.class);
        AiCallTask queued = task("cancel-me", "QUEUED", 1);
        AiCallTask cancelled = task("cancel-me", "CANCELLED", 1);
        cancelled.setCancelRequested(true);
        when(mapper.selectByTaskId("cancel-me")).thenReturn(queued, cancelled);
        when(mapper.requestCancel(org.mockito.ArgumentMatchers.eq("cancel-me"), any())).thenReturn(1);

        var result = new AiQueueOperationsService(mapper, waits).cancel("cancel-me");

        assertThat(result.getState()).isEqualTo("CANCELLED");
        verify(waits).complete(cancelled);
    }

    private AiCallTask task(String id, String state, long waitedSeconds) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AiCallTask task = new AiCallTask();
        task.setTaskId(id);
        task.setCallId("call-" + id);
        task.setCallerService("ai-review-service");
        task.setCallType("CHAT");
        task.setFeatureCode("AI_REVIEW");
        task.setOperationCode("EXPERIMENT_REVIEW");
        task.setEffectivePriority("P3");
        task.setState(state);
        task.setAttemptCount(0);
        task.setCancelRequested(false);
        task.setQueuedAt(now.minusSeconds(waitedSeconds));
        return task;
    }
}
