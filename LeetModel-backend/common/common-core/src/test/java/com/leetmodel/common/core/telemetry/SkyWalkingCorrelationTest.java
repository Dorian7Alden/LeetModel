package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkyWalkingCorrelationTest {

    @Test
    void shouldRemainNoOpWithoutAgent() {
        assertThat(SkyWalkingCorrelation.traceId()).isNull();
        assertThat(SkyWalkingCorrelation.spanId()).isNull();
        assertThat(SkyWalkingCorrelation.enrich(
                CorrelationSnapshot.EMPTY.withTraceId("business-trace")))
                .isEqualTo(CorrelationSnapshot.EMPTY.withTraceId("business-trace"));
    }

    @Test
    void shouldRejectSentinelsAndUnsafeAgentValues() {
        assertThat(SkyWalkingCorrelation.usable("N/A", 128)).isNull();
        assertThat(SkyWalkingCorrelation.usable("Ignored_Trace", 128)).isNull();
        assertThat(SkyWalkingCorrelation.usable("trace\r\nforged", 128)).isNull();
        assertThat(SkyWalkingCorrelation.usable("sw.trace-1", 128)).isEqualTo("sw.trace-1");
    }
}
