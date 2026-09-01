package com.leetmodel.common.core.logging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 按稳定事件维度聚合重复故障。
 *
 * <p>第一次故障立即输出，窗口内后续故障只计入 Prometheus；新窗口输出一次汇总，
 * 恢复时移除状态并返回自上次输出以来的抑制数量。key 只允许代码内固定的小写编码，
 * 不能传业务标识、异常文本或请求输入。</p>
 */
public final class FailureLogLimiter {

    public static final String SUPPRESSED_METRIC = "leetmodel.logging.suppressed";
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z][a-z0-9._-]{2,95}");
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final MeterRegistry registry;
    private final Clock clock;
    private final boolean enabled;
    private final long summaryIntervalMillis;
    private final int maxKeys;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FailureLogLimiter(MeterRegistry registry, LogRateLimitProperties properties) {
        this(registry, properties, Clock.systemUTC());
    }

    public FailureLogLimiter(MeterRegistry registry, LogRateLimitProperties properties, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.enabled = properties.isEnabled();
        Duration interval = properties.getSummaryInterval();
        this.summaryIntervalMillis = Math.max(1_000L,
                interval == null ? Duration.ofMinutes(1).toMillis() : interval.toMillis());
        this.maxKeys = Math.max(1, properties.getMaxKeys());
    }

    /** 供不启动 Spring 容器的纯单元测试或可选组件使用。 */
    public static FailureLogLimiter disabled() {
        LogRateLimitProperties properties = new LogRateLimitProperties();
        properties.setEnabled(false);
        return new FailureLogLimiter(Metrics.globalRegistry, properties);
    }

    /** 记录一次故障并决定是否输出首条或周期汇总。 */
    public Decision onFailure(String stableKey, String eventCode) {
        String key = requireStableKey(stableKey);
        String code = normalizeEventCode(eventCode);
        if (!enabled) return Decision.first();
        long now = clock.millis();

        if (!windows.containsKey(key) && windows.size() >= maxKeys) {
            incrementSuppressed(code);
            return Decision.suppressed(1L);
        }

        DecisionHolder holder = new DecisionHolder();
        windows.compute(key, (ignored, current) -> {
            if (current == null) {
                holder.decision = Decision.first();
                return new Window(now, 0L, 1L, code);
            }
            long total = current.totalFailures() + 1L;
            if (now - current.lastEmissionMillis() >= summaryIntervalMillis) {
                holder.decision = Decision.summary(current.suppressedSinceEmission(), total);
                return new Window(now, 0L, total, current.eventCode());
            }
            long suppressed = current.suppressedSinceEmission() + 1L;
            holder.decision = Decision.suppressed(suppressed);
            return new Window(current.lastEmissionMillis(), suppressed, total, current.eventCode());
        });
        if (holder.decision.kind() == Kind.SUPPRESSED) incrementSuppressed(code);
        return holder.decision;
    }

    /** 记录依赖恢复；没有先前故障时不产生恢复日志。 */
    public Decision onRecovery(String stableKey) {
        String key = requireStableKey(stableKey);
        if (!enabled) return Decision.none();
        Window previous = windows.remove(key);
        return previous == null
                ? Decision.none()
                : Decision.recovery(previous.suppressedSinceEmission(), previous.totalFailures());
    }

    private void incrementSuppressed(String eventCode) {
        Counter.builder(SUPPRESSED_METRIC)
                .description("Repeated failure log events suppressed before append")
                .tag("event_code", eventCode)
                .register(registry)
                .increment();
    }

    private String requireStableKey(String value) {
        if (value == null || !STABLE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("log rate-limit key must be a stable code");
        }
        return value;
    }

    private String normalizeEventCode(String value) {
        if (value == null) return LogEventCodes.UNCLASSIFIED;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return EVENT_CODE.matcher(normalized).matches()
                ? normalized : LogEventCodes.UNCLASSIFIED;
    }

    public enum Kind {
        NONE,
        FIRST,
        SUMMARY,
        RECOVERY,
        SUPPRESSED
    }

    /** 调用方只在 {@link #shouldLog()} 为真时写日志。 */
    public record Decision(Kind kind, long suppressedCount, long totalFailures) {
        public boolean shouldLog() {
            return kind == Kind.FIRST || kind == Kind.SUMMARY || kind == Kind.RECOVERY;
        }

        private static Decision none() {
            return new Decision(Kind.NONE, 0L, 0L);
        }

        private static Decision first() {
            return new Decision(Kind.FIRST, 0L, 1L);
        }

        private static Decision summary(long suppressedCount, long totalFailures) {
            return new Decision(Kind.SUMMARY, suppressedCount, totalFailures);
        }

        private static Decision recovery(long suppressedCount, long totalFailures) {
            return new Decision(Kind.RECOVERY, suppressedCount, totalFailures);
        }

        private static Decision suppressed(long suppressedCount) {
            return new Decision(Kind.SUPPRESSED, suppressedCount, 0L);
        }
    }

    private record Window(long lastEmissionMillis, long suppressedSinceEmission,
                          long totalFailures, String eventCode) {
    }

    private static final class DecisionHolder {
        private Decision decision;
    }
}
