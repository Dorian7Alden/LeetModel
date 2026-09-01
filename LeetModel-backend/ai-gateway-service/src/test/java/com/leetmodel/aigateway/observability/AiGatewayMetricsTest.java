package com.leetmodel.aigateway.observability;

import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.common.core.metrics.MetricTagPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiGatewayMetricsTest {

    @Test
    @SuppressWarnings("unchecked")
    void recordsLatencyTokensCostUnknownAndOnlyBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        AiGatewayMetrics metrics = new AiGatewayMetrics(provider, mock(AiCallTaskMapper.class));
        AiCallLog call = new AiCallLog();
        call.setCallType("CHAT");
        call.setPriority("P0");
        call.setStatus("SUCCEEDED");
        call.setQueueMs(12L);
        call.setExecutionMs(40L);
        call.setTotalMs(52L);
        call.setInputTokens(10L);
        call.setOutputTokens(5L);
        call.setUsageCompleteness("COMPLETE");
        call.setCostAmount(new BigDecimal("0.0012"));
        call.setCostCurrency("USD");
        call.setCostSource("PRICE_SNAPSHOT_ESTIMATED");
        call.setCostCompleteness("COMPLETE");

        metrics.call(call);
        metrics.recovered("upstream_result_unknown");

        assertThat(registry.get("leetmodel.ai.queue.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("leetmodel.ai.execution.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("leetmodel.ai.end_to_end.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("leetmodel.ai.tokens").tag("token_type", "input")
                .counter().count()).isEqualTo(10D);
        assertThat(registry.get("leetmodel.ai.cost").summary().count()).isEqualTo(1L);
        assertThat(registry.get("leetmodel.ai.recovery")
                .tag("outcome", "upstream_result_unknown").counter().count()).isEqualTo(1D);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).noneMatch(tag ->
                        MetricTagPolicy.isForbiddenIdTag(tag.getKey())));
    }
}
