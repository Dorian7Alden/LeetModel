package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.CostEnrichmentProperties;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import com.leetmodel.aigateway.observability.AiGatewayMetrics;
import com.leetmodel.common.core.logging.FailureLogLimiter;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 使用本地不可变价格快照异步补全估算费用。 */
@Slf4j
@Service
public class AiCostEnrichmentService {
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final String FAILURE_KEY = "ai.cost-enrichment";

    private final AiCallLogMapper mapper;
    private final CostEnrichmentProperties properties;
    private final Clock clock;
    private final AiGatewayMetrics metrics;
    private final FailureLogLimiter failureLogLimiter;

    @Autowired
    public AiCostEnrichmentService(AiCallLogMapper mapper, CostEnrichmentProperties properties,
                                   AiGatewayMetrics metrics,
                                   FailureLogLimiter failureLogLimiter) {
        this(mapper, properties, Clock.systemUTC(), metrics, failureLogLimiter);
    }

    AiCostEnrichmentService(AiCallLogMapper mapper, CostEnrichmentProperties properties,
                            Clock clock, AiGatewayMetrics metrics) {
        this(mapper, properties, clock, metrics, FailureLogLimiter.disabled());
    }

    AiCostEnrichmentService(AiCallLogMapper mapper, CostEnrichmentProperties properties,
                            Clock clock, AiGatewayMetrics metrics,
                            FailureLogLimiter failureLogLimiter) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
        this.failureLogLimiter = failureLogLimiter;
    }

    @Scheduled(fixedDelayString = "${ai.cost-enrichment.poll-delay-ms:60000}")
    public void enrichDue() {
        if (!properties.isEnabled()) return;
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            mapper.selectCostEnrichmentDue(now, properties.getBatchSize())
                    .forEach(record -> enrichOne(record, now));
            logRecovery();
        } catch (RuntimeException exception) {
            logFailure(exception);
        }
    }

    private void logFailure(RuntimeException exception) {
        FailureLogLimiter.Decision decision = failureLogLimiter.onFailure(
                FAILURE_KEY, LogEventCodes.DEPENDENCY_CALL_FAILED);
        if (!decision.shouldLog()) return;
        log.atWarn()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DEPENDENCY_CALL_FAILED)
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, "AI_COST_ENRICHMENT")
                .addKeyValue(LogFieldNames.EXCEPTION_TYPE, exception.getClass().getName())
                .addKeyValue(LogFieldNames.SUPPRESSED_COUNT, decision.suppressedCount())
                .log(decision.kind() == FailureLogLimiter.Kind.SUMMARY
                        ? "AI cost enrichment dependency remains unavailable"
                        : "AI cost enrichment dependency unavailable");
    }

    private void logRecovery() {
        FailureLogLimiter.Decision decision = failureLogLimiter.onRecovery(FAILURE_KEY);
        if (!decision.shouldLog()) return;
        log.atInfo()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DEPENDENCY_CALL_RECOVERED)
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, "AI_COST_ENRICHMENT")
                .addKeyValue(LogFieldNames.SUPPRESSED_COUNT, decision.suppressedCount())
                .log("AI cost enrichment dependency recovered");
    }

    void enrichOne(AiCallLog record, LocalDateTime now) {
        Estimate estimate = estimate(record);
        if (estimate != null) {
            if (mapper.completeEstimatedCost(record.getId(), estimate.amount(), estimate.currency(),
                    estimate.snapshotVersion(), now) == 1) {
                metrics.costEnriched(record.getCallType(), estimate.amount(), estimate.currency(),
                        "PRICE_SNAPSHOT_ESTIMATED");
            }
            return;
        }
        int attempts = record.getCostEnrichmentAttempts() == null
                ? 0 : record.getCostEnrichmentAttempts();
        boolean exhausted = attempts + 1 >= properties.getMaxAttempts();
        int updated = mapper.recordCostEnrichmentMiss(record.getId(),
                exhausted ? "FINAL_UNKNOWN" : "RETRY_WAIT",
                now, exhausted ? null : now.plus(properties.getRetryDelay()));
        if (updated == 1) metrics.costEnrichmentMiss(exhausted);
    }

    private Estimate estimate(AiCallLog record) {
        if (!"COMPLETE".equals(record.getUsageCompleteness())
                || record.getInputTokens() == null || record.getOutputTokens() == null) return null;
        CostEnrichmentProperties.PriceSnapshot snapshot = properties.getSnapshots().get(record.getModel());
        if (snapshot == null || snapshot.getInputPerMillionTokens() == null
                || snapshot.getOutputPerMillionTokens() == null) return null;

        long cacheHit = value(record.getCacheHitTokens());
        long cacheCreation = value(record.getCacheCreationTokens());
        if (cacheHit > 0 && snapshot.getCacheHitPerMillionTokens() == null) return null;
        if (cacheCreation > 0 && snapshot.getCacheCreationPerMillionTokens() == null) return null;
        long regularInput = record.getInputTokens() - cacheHit - cacheCreation;
        if (regularInput < 0) return null;

        BigDecimal amount = priced(regularInput, snapshot.getInputPerMillionTokens())
                .add(priced(record.getOutputTokens(), snapshot.getOutputPerMillionTokens()))
                .add(priced(cacheHit, snapshot.getCacheHitPerMillionTokens()))
                .add(priced(cacheCreation, snapshot.getCacheCreationPerMillionTokens()))
                .setScale(12, RoundingMode.HALF_UP);
        return new Estimate(amount, snapshot.getCurrency(), snapshot.getVersion());
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal priced(long tokens, BigDecimal perMillion) {
        if (tokens == 0 || perMillion == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(tokens).multiply(perMillion).divide(ONE_MILLION, 18, RoundingMode.HALF_UP);
    }

    private record Estimate(BigDecimal amount, String currency, String snapshotVersion) {}
}
