package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryFieldPolicyTest {

    @Test
    void eventCodeMustBeStableAndMustNotContainRuntimeIdentifiers() {
        assertThat(TelemetryFieldPolicy.requireEventCode("OUTBOX_PUBLISH_RETRY"))
                .isEqualTo("OUTBOX_PUBLISH_RETRY");
        assertThat(TelemetryFieldPolicy.requireEventCode("HTTP_5XX_RESPONSE"))
                .isEqualTo("HTTP_5XX_RESPONSE");

        assertThatThrownBy(() -> TelemetryFieldPolicy.requireEventCode("retry-12345"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TelemetryFieldPolicy.requireEventCode("OUTBOX_RETRY_123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void highCardinalityIdentifiersMustNotBecomeMetricLabels() {
        assertThat(TelemetryFieldPolicy.requireLowCardinalityLabel("service")).isEqualTo("service");
        assertThat(TelemetryFieldPolicy.requireLowCardinalityLabel("failure_category"))
                .isEqualTo("failure_category");

        assertThatThrownBy(() -> TelemetryFieldPolicy.requireLowCardinalityLabel("traceId"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TelemetryFieldPolicy.requireLowCardinalityLabel("domain_task_id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TelemetryFieldPolicy.requireLowCardinalityLabel("aiCallId"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serviceResourceMustContainOnlyStartupStableValues() {
        TelemetryResource resource = new TelemetryResource(
                "user-service", "dev", "2.0.0", "user-service-1"
        );

        assertThat(resource.service()).isEqualTo("user-service");
        assertThatThrownBy(() -> new TelemetryResource(
                "user-service", "dev", "2.0.0", "instance/from/request"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
