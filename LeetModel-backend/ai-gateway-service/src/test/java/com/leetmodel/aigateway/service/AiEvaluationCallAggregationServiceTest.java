package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiEvaluationCallAggregationServiceTest {
    private final AiCallLogMapper mapper = mock(AiCallLogMapper.class);
    private final AiEvaluationCallAggregationService service =
            new AiEvaluationCallAggregationService(mapper);

    @Test
    void aggregatesTokensCostsAndThreeDurationsWithCompleteness() {
        AiCallLog actual = call("SUCCEEDED", 10L, 5L, "COMPLETE", "1.25", "CNY",
                "PROVIDER_REPORTED", 20L, 80L, 100L);
        AiCallLog missing = call("FAILED", null, null, "UNKNOWN", null, null,
                "UNKNOWN", null, null, null);
        when(mapper.selectList(any())).thenReturn(List.of(actual, missing));

        var result = service.aggregate("20");

        assertThat(result.getCallCount()).isEqualTo(2);
        assertThat(result.getSucceededCallCount()).isEqualTo(1);
        assertThat(result.getFailedCallCount()).isEqualTo(1);
        assertThat(result.getInputTokens()).isEqualTo(10L);
        assertThat(result.getOutputTokens()).isEqualTo(5L);
        assertThat(result.getUsageCompleteCount()).isEqualTo(1);
        assertThat(result.getUsageMissingCount()).isEqualTo(1);
        assertThat(result.getCostTotals()).containsEntry("CNY", new BigDecimal("1.25"));
        assertThat(result.getActualCostCount()).isEqualTo(1);
        assertThat(result.getCostMissingCount()).isEqualTo(1);
        assertThat(result.getAverageQueueMs()).isEqualTo(20L);
        assertThat(result.getAverageExecutionMs()).isEqualTo(80L);
        assertThat(result.getAverageTotalMs()).isEqualTo(100L);
        assertThat(result.getDurationMissingCount()).isEqualTo(1);
    }

    private AiCallLog call(String status, Long input, Long output, String usageCompleteness,
                           String cost, String currency, String costSource,
                           Long queue, Long execution, Long total) {
        AiCallLog call = new AiCallLog();
        call.setStatus(status);
        call.setInputTokens(input);
        call.setOutputTokens(output);
        call.setUsageCompleteness(usageCompleteness);
        call.setCostAmount(cost == null ? null : new BigDecimal(cost));
        call.setCostCurrency(currency);
        call.setCostSource(costSource);
        call.setCostCompleteness(cost == null ? "UNKNOWN" : "COMPLETE");
        call.setQueueMs(queue);
        call.setExecutionMs(execution);
        call.setTotalMs(total);
        return call;
    }
}
