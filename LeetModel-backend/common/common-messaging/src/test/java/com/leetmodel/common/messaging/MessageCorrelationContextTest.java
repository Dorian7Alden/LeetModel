package com.leetmodel.common.messaging;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCorrelationContextTest {

    @AfterEach
    void clearMdc() {
        CorrelationContext.clear();
    }

    @Test
    void messageScopeMustRestoreEnvelopeAndTaskFieldsThenCleanWorkerThread() {
        MessageEnvelopeV1<Map<String, String>> envelope = new MessageEnvelopeV1<>(
                "00000000-0000-4000-8000-000000000001",
                "REVIEW_TASK_READY",
                1,
                "submission-service",
                "submission",
                "42",
                "review:42:v1",
                Instant.parse("2026-09-01T00:00:00Z"),
                "trace-message",
                "operation-message",
                Map.of("submissionId", "42")
        );

        try (CorrelationContext.Scope ignored = MessageCorrelationContext.open(
                envelope, "task-42", 2, "call-42")) {
            CorrelationSnapshot current = CorrelationContext.capture();
            assertThat(current.traceId()).isEqualTo("trace-message");
            assertThat(current.operationId()).isEqualTo("operation-message");
            assertThat(current.eventId()).isEqualTo(envelope.eventId());
            assertThat(current.domainTaskId()).isEqualTo("task-42");
            assertThat(current.attemptNo()).isEqualTo(2);
            assertThat(current.aiCallId()).isEqualTo("call-42");
        }

        assertThat(CorrelationContext.capture()).isEqualTo(CorrelationSnapshot.EMPTY);
    }

    @Test
    void envelopeFactoryMustCopyOnlyTrustedOperationContext() {
        MessageEnvelopeFactory factory = new MessageEnvelopeFactory(
                "submission-service", Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"),
                java.time.ZoneOffset.UTC)
        );
        CorrelationSnapshot trusted = CorrelationSnapshot.EMPTY
                .withTraceId("trace-factory")
                .withOperationId("operation-factory");

        MessageEnvelopeV1<Map<String, String>> envelope;
        try (CorrelationContext.Scope ignored = CorrelationContext.open(trusted)) {
            envelope = factory.create("REVIEW_TASK_READY", "submission", "42",
                    "review:42:v1", CorrelationContext.traceId(), Map.of("submissionId", "42"));
        }

        assertThat(envelope.traceId()).isEqualTo("trace-factory");
        assertThat(envelope.operationId()).isEqualTo("operation-factory");
        assertThat(envelope.eventId()).matches("[0-9a-f-]{36}");
    }
}
