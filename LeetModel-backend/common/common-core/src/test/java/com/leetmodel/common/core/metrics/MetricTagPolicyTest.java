package com.leetmodel.common.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTagPolicyTest {

    @Test
    void rejectsBusinessIdentifiersButAllowsBoundedDimensions() {
        assertThat(MetricTagPolicy.isForbiddenIdTag("user_id")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("teamId")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("submission-id")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("traceId")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("operation_id")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("event.id")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("domainTaskId")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("evaluation_task_id")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("attempt_no")).isTrue();
        assertThat(MetricTagPolicy.isForbiddenIdTag("ai_call_id")).isTrue();

        assertThat(MetricTagPolicy.isForbiddenIdTag("operation_code")).isFalse();
        assertThat(MetricTagPolicy.isForbiddenIdTag("priority")).isFalse();
        assertThat(MetricTagPolicy.isForbiddenIdTag("route")).isFalse();
    }

    @Test
    void meterFilterDropsMetersContainingForbiddenIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MetricPolicyAutoConfiguration()
                .leetModelForbiddenMetricTagFilter());

        Counter.builder("leetmodel.safe").tag("priority", "P0").register(registry).increment();
        Counter.builder("leetmodel.unsafe").tag("task_id", "123").register(registry).increment();

        assertThat(registry.find("leetmodel.safe").counter()).isNotNull();
        assertThat(registry.find("leetmodel.unsafe").counter()).isNull();
    }
}
