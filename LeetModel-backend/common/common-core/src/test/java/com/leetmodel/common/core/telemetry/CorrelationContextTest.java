package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationContextTest {

    @AfterEach
    void clearMdc() {
        CorrelationContext.clear();
    }

    @Test
    void scopeMustRestoreEveryPreviousCorrelationField() {
        CorrelationSnapshot previous = new CorrelationSnapshot(
                "trace-parent", "sw-parent", "7", "operation-parent", null,
                null, null, null
        );
        CorrelationSnapshot worker = new CorrelationSnapshot(
                "trace-worker", "sw-worker", "9", "operation-worker", "event-worker",
                "task-42", 3, "call-42"
        );
        CorrelationContext.replace(previous);

        try (CorrelationContext.Scope ignored = CorrelationContext.open(worker)) {
            assertThat(CorrelationContext.capture()).isEqualTo(worker);
        }

        assertThat(CorrelationContext.capture()).isEqualTo(previous);
    }

    @Test
    void newIdMustBeBoundedAndHeaderSafe() {
        String generated = CorrelationContext.newId();

        assertThat(generated).hasSize(32).matches("[a-f0-9]{32}");
        assertThat(CorrelationContext.isValidHttpId(generated)).isTrue();
        assertThat(CorrelationContext.isValidHttpId("forged\r\nheader")).isFalse();
        assertThat(CorrelationContext.isValidHttpId("x".repeat(101))).isFalse();
    }
}
