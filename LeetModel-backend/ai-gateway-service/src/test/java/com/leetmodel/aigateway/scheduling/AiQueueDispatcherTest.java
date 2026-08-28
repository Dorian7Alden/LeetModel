package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.config.AiSchedulingProperties;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class AiQueueDispatcherTest {

    private AiQueueDispatcher dispatcher;
    private final AiTaskWaitRegistry waitRegistry = new AiTaskWaitRegistry();

    @AfterEach
    void shutdown() {
        if (dispatcher != null) dispatcher.shutdown();
        waitRegistry.shutdown();
    }

    @Test
    void expiresBeforeDispatch() {
        AiCallTaskMapper tasks = mock(AiCallTaskMapper.class);
        AiCallAttemptMapper attempts = mock(AiCallAttemptMapper.class);
        AiCallTask expired = task("expired", "P0");
        expired.setDeadline(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(tasks.selectQueued(500)).thenReturn(List.of(expired));
        when(tasks.expireBeforeDispatch(anyString(), anyLong(), any())).thenReturn(1);
        dispatcher = dispatcher(tasks, attempts, ignored -> "result", new AiSchedulingProperties());

        assertThat(dispatcher.dispatchOnce()).isFalse();
        verify(tasks).expireBeforeDispatch(anyString(), anyLong(), any());
        verify(tasks, never()).claimQueued(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    void reservesOneRunningPermitForP0() throws Exception {
        AiCallTaskMapper tasks = mock(AiCallTaskMapper.class);
        AiCallAttemptMapper attempts = mock(AiCallAttemptMapper.class);
        CopyOnWriteArrayList<AiCallTask> queued = new CopyOnWriteArrayList<>(List.of(
                task("p4-1", "P4"), task("p4-2", "P4"), task("p4-3", "P4"), task("p4-4", "P4")));
        when(tasks.selectQueued(500)).thenAnswer(ignored -> List.copyOf(queued));
        when(tasks.claimQueued(anyString(), anyLong(), anyString(), any(), any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            queued.removeIf(task -> task.getTaskId().equals(id));
            return 1;
        });
        when(tasks.selectByTaskId(anyString())).thenAnswer(invocation -> leased(invocation.getArgument(0)));
        when(tasks.transition(anyString(), anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        when(tasks.completeRunning(anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(1);
        when(attempts.transition(anyString(), anyString(), anyString(), any(), any())).thenReturn(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(4);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AiQueuedTaskExecutor executor = ignored -> {
            int running = active.incrementAndGet();
            maximum.accumulateAndGet(running, Math::max);
            started.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            return "result";
        };
        dispatcher = dispatcher(tasks, attempts, executor, new AiSchedulingProperties());

        assertThat(dispatcher.dispatchOnce()).isTrue();
        assertThat(dispatcher.dispatchOnce()).isTrue();
        assertThat(dispatcher.dispatchOnce()).isTrue();
        assertThat(dispatcher.dispatchOnce()).isFalse();
        queued.add(task("p0", "P0"));
        assertThat(dispatcher.dispatchOnce()).isTrue();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(maximum.get()).isEqualTo(4);
        release.countDown();
    }

    @Test
    void doesNotRedispatchWhenResultPersistenceBecomesUncertain() throws Exception {
        AiCallTaskMapper tasks = mock(AiCallTaskMapper.class);
        AiCallAttemptMapper attempts = mock(AiCallAttemptMapper.class);
        AiCallTask queued = task("uncertain", "P3");
        when(tasks.selectQueued(500)).thenReturn(List.of(queued), List.of());
        when(tasks.claimQueued(anyString(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(tasks.selectByTaskId(anyString())).thenAnswer(ignored -> leased("uncertain"));
        when(tasks.transition(anyString(), anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        when(tasks.completeRunning(anyString(), anyString(), anyString(), any(), any(), any())).thenReturn(0);
        CountDownLatch unknown = new CountDownLatch(1);
        when(attempts.transition(anyString(), anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            if ("UNKNOWN".equals(invocation.getArgument(2))) unknown.countDown();
            return 1;
        });
        AtomicInteger upstreamCalls = new AtomicInteger();
        dispatcher = dispatcher(tasks, attempts, ignored -> {
            upstreamCalls.incrementAndGet();
            return "known-result";
        }, new AiSchedulingProperties());

        assertThat(dispatcher.dispatchOnce()).isTrue();
        assertThat(unknown.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(dispatcher.dispatchOnce()).isFalse();
        assertThat(upstreamCalls.get()).isEqualTo(1);
        verify(attempts).transition(anyString(), anyString(), org.mockito.ArgumentMatchers.eq("UNKNOWN"),
                org.mockito.ArgumentMatchers.eq("AI_UPSTREAM_RESULT_UNKNOWN"), any());
        verify(tasks, times(1)).claimQueued(anyString(), anyLong(), anyString(), any(), any());
    }

    private AiQueueDispatcher dispatcher(AiCallTaskMapper tasks, AiCallAttemptMapper attempts,
                                         AiQueuedTaskExecutor executor, AiSchedulingProperties properties) {
        return new AiQueueDispatcher(tasks, attempts, new AiFairSchedulingPolicy(), executor, properties,
                new AiRateLimitBackoff(), waitRegistry);
    }

    private AiCallTask task(String id, String priority) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        AiCallTask task = new AiCallTask();
        task.setTaskId(id);
        task.setEffectivePriority(priority);
        task.setQueuedAt(now.minusSeconds(1));
        task.setDeadline(now.plusMinutes(2));
        task.setMaxQueueWaitMs(60_000L);
        task.setVersion(0L);
        task.setAttemptCount(0);
        task.setCancelRequested(false);
        return task;
    }

    private AiCallTask leased(String id) {
        AiCallTask task = task(id, id.startsWith("p0") ? "P0" : "P4");
        task.setState("LEASED");
        task.setVersion(1L);
        return task;
    }
}
