package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.CostEnrichmentProperties;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCostEnrichmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private AiCallLogMapper mapper;
    private CostEnrichmentProperties properties;
    private AiCostEnrichmentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiCallLogMapper.class);
        properties = new CostEnrichmentProperties();
        properties.setMaxAttempts(2);
        CostEnrichmentProperties.PriceSnapshot snapshot = new CostEnrichmentProperties.PriceSnapshot();
        snapshot.setVersion("PRICE_DEEPSEEK_20260828");
        snapshot.setCurrency("CNY");
        snapshot.setInputPerMillionTokens(new BigDecimal("2.00"));
        snapshot.setOutputPerMillionTokens(new BigDecimal("4.00"));
        snapshot.setCacheHitPerMillionTokens(new BigDecimal("0.20"));
        properties.setSnapshots(Map.of("deepseek-v4-flash", snapshot));
        service = new AiCostEnrichmentService(mapper, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldWriteAbsoluteEstimateFromImmutableSnapshot() {
        AiCallLog record = completeRecord();

        service.enrichOne(record, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mapper).completeEstimatedCost(eq(1L), amount.capture(), eq("CNY"),
                eq("PRICE_DEEPSEEK_20260828"), any(LocalDateTime.class));
        assertThat(amount.getValue()).isEqualByComparingTo("0.000262000000");
    }

    @Test
    void shouldRetryThenFinishUnknownWhenNoUsableSnapshot() {
        AiCallLog first = completeRecord();
        first.setModel("unpriced-model");
        first.setCostEnrichmentAttempts(0);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        service.enrichOne(first, now);
        first.setCostEnrichmentAttempts(1);
        service.enrichOne(first, now);

        verify(mapper).recordCostEnrichmentMiss(1L, "RETRY_WAIT", now,
                now.plus(properties.getRetryDelay()));
        verify(mapper).recordCostEnrichmentMiss(1L, "FINAL_UNKNOWN", now, null);
    }

    @Test
    void batchFailureMustNotAffectBusinessCalls() {
        doThrow(new IllegalStateException("new-api or database unavailable"))
                .when(mapper).selectCostEnrichmentDue(any(), eq(properties.getBatchSize()));

        assertThatCode(service::enrichDue).doesNotThrowAnyException();
    }

    private AiCallLog completeRecord() {
        AiCallLog record = new AiCallLog();
        record.setId(1L);
        record.setModel("deepseek-v4-flash");
        record.setUsageCompleteness("COMPLETE");
        record.setInputTokens(100L);
        record.setOutputTokens(20L);
        record.setCacheHitTokens(10L);
        record.setCostEnrichmentAttempts(0);
        return record;
    }
}
