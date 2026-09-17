package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        CorrelationContext.clear();
    }

    @Test
    void threadPoolTaskMustPropagateSnapshotAndLeaveNoMdcBehind() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicReference<CorrelationSnapshot> observed = new AtomicReference<>();
        CorrelationSnapshot submitted = CorrelationSnapshot.EMPTY
                .withTraceId("trace-submitted")
                .withOperationId("operation-submitted")
                .withDomainTask("task-7", 2);
        Runnable decorated;
        try (CorrelationContext.Scope ignored = CorrelationContext.open(submitted)) {
            decorated = CorrelationTaskDecorator.INSTANCE.decorate(
                    () -> observed.set(CorrelationContext.capture())
            );
        }

        try {
            worker.submit(decorated).get();
            Future<CorrelationSnapshot> after = worker.submit(CorrelationContext::capture);

            assertThat(observed.get()).isEqualTo(submitted);
            assertThat(after.get()).isEqualTo(CorrelationSnapshot.EMPTY);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void failedThreadPoolTaskMustAlsoLeaveNoMdcBehind() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Runnable decorated;
        try (CorrelationContext.Scope ignored = CorrelationContext.open(
                CorrelationSnapshot.EMPTY.withTraceId("trace-failed"))) {
            decorated = CorrelationTaskDecorator.INSTANCE.decorate(() -> {
                assertThat(CorrelationContext.traceId()).isEqualTo("trace-failed");
                throw new IllegalStateException("expected failure");
            });
        }

        try {
            Future<?> failed = worker.submit(decorated);
            org.assertj.core.api.Assertions.assertThatThrownBy(failed::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(worker.submit(CorrelationContext::capture).get())
                    .isEqualTo(CorrelationSnapshot.EMPTY);
        } finally {
            worker.shutdownNow();
        }
    }
}
