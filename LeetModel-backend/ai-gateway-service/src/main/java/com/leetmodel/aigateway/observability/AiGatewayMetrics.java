package com.leetmodel.aigateway.observability;

import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI 调度、耗时、用量、费用与结果完整性的低基数指标。
 */
@Component
public class AiGatewayMetrics {

    private static final List<String> PRIORITIES = List.of("P0", "P1", "P2", "P3", "P4");
    private static final List<String> ACTIVE_STATES = List.of("QUEUED", "LEASED", "RUNNING");
    private static final Set<String> CALL_TYPES = Set.of("CHAT", "EMBEDDING");
    private static final Set<String> COMPLETENESS = Set.of("COMPLETE", "PARTIAL", "UNKNOWN");
    private static final Set<String> COST_SOURCES = Set.of(
            "NEW_API_ACTUAL", "PRICE_SNAPSHOT_ESTIMATED", "UNKNOWN");

    private final MeterRegistry registry;

    public AiGatewayMetrics(ObjectProvider<MeterRegistry> registryProvider, AiCallTaskMapper taskMapper) {
        this.registry = registryProvider.getIfAvailable();
        if (registry == null) return;
        for (String priority : PRIORITIES) {
            for (String state : ACTIVE_STATES) {
                Gauge.builder("leetmodel.ai.queue.tasks", taskMapper,
                                mapper -> mapper.countByPriorityAndState(priority, state))
                        .tag("priority", priority.toLowerCase(Locale.ROOT))
                        .tag("state", state.toLowerCase(Locale.ROOT))
                        .description("AI tasks by fixed priority and active state")
                        .register(registry);
            }
            Gauge.builder("leetmodel.ai.queue.oldest.seconds", taskMapper,
                            mapper -> mapper.oldestQueuedAgeSeconds(priority))
                    .tag("priority", priority.toLowerCase(Locale.ROOT))
                    .description("Oldest queued AI task by fixed priority")
                    .register(registry);
        }
        Gauge.builder("leetmodel.ai.queue.expired.leases", taskMapper,
                        AiCallTaskMapper::countExpiredLeases)
                .register(registry);
        Gauge.builder("leetmodel.ai.results.unknown", taskMapper,
                        AiCallTaskMapper::countUnknownResults)
                .description("Persisted AI tasks with uncertain upstream result")
                .register(registry);
    }

    /** 将 AI 网关自建固定线程池纳入标准 executor 指标。 */
    public ExecutorService monitor(ExecutorService executor, String name) {
        if (registry == null) return executor;
        try {
            return ExecutorServiceMetrics.monitor(registry, executor, name);
        } catch (RuntimeException ignored) {
            return executor;
        }
    }

    public void admission(String priority, String outcome) {
        increment("leetmodel.ai.admission", "priority", priority(priority),
                "outcome", bounded(outcome, Set.of("accepted", "idempotent", "rejected")));
    }

    public void dispatched(String priority, String outcome) {
        increment("leetmodel.ai.dispatch", "priority", priority(priority),
                "outcome", bounded(outcome, Set.of("claimed", "state_conflict", "executor_rejected")));
    }

    public void recovered(String outcome) {
        increment("leetmodel.ai.recovery", "outcome", bounded(outcome,
                Set.of("requeued_before_dispatch", "upstream_result_unknown", "released_without_attempt")));
    }

    public void terminal(AiCallTask task, String outcome) {
        if (task == null) return;
        increment("leetmodel.ai.tasks.completed",
                "priority", priority(task.getEffectivePriority()),
                "call_type", callType(task.getCallType()),
                "outcome", bounded(outcome,
                        Set.of("succeeded", "failed", "expired", "cancelled", "upstream_result_unknown")));
    }

    /** 记录异步价格快照补全后的最终费用，不重复累计调用与 Token。 */
    public void costEnriched(String callType, BigDecimal amount, String currency, String source) {
        if (registry == null || amount == null || amount.signum() < 0) return;
        try {
            recordCost(callType(callType), amount, currency, source);
            increment("leetmodel.ai.cost.enrichment", "outcome", "completed");
        } catch (RuntimeException ignored) {
            // 费用事实已持久化，指标失败不得影响补录结果。
        }
    }

    public void costEnrichmentMiss(boolean exhausted) {
        increment("leetmodel.ai.cost.enrichment", "outcome",
                exhausted ? "final_unknown" : "retry");
    }

    /** 以调用事实记录分段延迟、Token、费用和完整性。 */
    public void call(AiCallLog call) {
        if (registry == null || call == null) return;
        try {
            String callType = callType(call.getCallType());
            String priority = priority(call.getPriority());
            String outcome = "SUCCEEDED".equals(call.getStatus()) ? "succeeded" : "failed";
            increment("leetmodel.ai.calls", "call_type", callType,
                    "priority", priority, "outcome", outcome);
            recordTimer("leetmodel.ai.queue.duration", call.getQueueMs(), callType, priority, outcome);
            recordTimer("leetmodel.ai.execution.duration", call.getExecutionMs(), callType, priority, outcome);
            recordTimer("leetmodel.ai.end_to_end.duration", call.getTotalMs(), callType, priority, outcome);
            recordTokens(call, callType);
            recordCost(call, callType);
            increment("leetmodel.ai.usage.completeness", "call_type", callType,
                    "completeness", completeness(call.getUsageCompleteness()));
            increment("leetmodel.ai.cost.completeness", "call_type", callType,
                    "completeness", completeness(call.getCostCompleteness()));
        } catch (RuntimeException ignored) {
            // 指标故障不得改变已完成的 AI 调用结果。
        }
    }

    private void recordTokens(AiCallLog call, String callType) {
        token(call.getInputTokens(), callType, "input");
        token(call.getOutputTokens(), callType, "output");
        token(call.getReasoningTokens(), callType, "reasoning");
        token(call.getCacheHitTokens(), callType, "cache_hit");
        token(call.getCacheCreationTokens(), callType, "cache_creation");
        token(call.getCacheMissTokens(), callType, "cache_miss");
    }

    private void token(Long amount, String callType, String tokenType) {
        if (registry != null && amount != null && amount >= 0L) {
            registry.counter("leetmodel.ai.tokens", "call_type", callType,
                    "token_type", tokenType).increment(amount.doubleValue());
        }
    }

    private void recordCost(AiCallLog call, String callType) {
        BigDecimal amount = call.getCostAmount();
        if (amount == null || amount.signum() < 0) return;
        recordCost(callType, amount, call.getCostCurrency(), call.getCostSource());
    }

    private void recordCost(String callType, BigDecimal amount, String currencyValue, String sourceValue) {
        String currency = currencyValue;
        if (currency == null || !currency.matches("[A-Z]{3}")) currency = "unknown";
        String source = sourceValue != null && COST_SOURCES.contains(sourceValue.toUpperCase(Locale.ROOT))
                ? sourceValue.toLowerCase(Locale.ROOT) : "unknown";
        DistributionSummary.builder("leetmodel.ai.cost")
                .tag("call_type", callType)
                .tag("currency", currency.toLowerCase(Locale.ROOT))
                .tag("source", source)
                .register(registry)
                .record(amount.doubleValue());
    }

    private void recordTimer(String name, Long milliseconds,
                             String callType, String priority, String outcome) {
        if (milliseconds == null) return;
        Timer.builder(name)
                .tags("call_type", callType, "priority", priority, "outcome", outcome)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofMinutes(15))
                .register(registry)
                .record(Math.max(0L, milliseconds), TimeUnit.MILLISECONDS);
    }

    private void increment(String name, String... tags) {
        if (registry == null) return;
        try {
            registry.counter(name, tags).increment();
        } catch (RuntimeException ignored) {
            // 指标故障不得改变调度、恢复或计量事实。
        }
    }

    private String priority(String value) {
        return value != null && PRIORITIES.contains(value.toUpperCase(Locale.ROOT))
                ? value.toLowerCase(Locale.ROOT) : "unknown";
    }

    private String callType(String value) {
        return value != null && CALL_TYPES.contains(value.toUpperCase(Locale.ROOT))
                ? value.toLowerCase(Locale.ROOT) : "unknown";
    }

    private String completeness(String value) {
        return value != null && COMPLETENESS.contains(value.toUpperCase(Locale.ROOT))
                ? value.toLowerCase(Locale.ROOT) : "unknown";
    }

    private String bounded(String value, Set<String> allowed) {
        if (value == null) return "unknown";
        String normalized = value.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : "unknown";
    }
}
