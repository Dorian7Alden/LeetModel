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

    /**
     * 构建一个关闭限频的禁用实例，供单元测试或无 Spring 环境使用。
     *
     * @return 始终判定放行日志的 FailureLogLimiter 实例
     */
    public static FailureLogLimiter disabled() {
        LogRateLimitProperties properties = new LogRateLimitProperties();
        properties.setEnabled(false);
        return new FailureLogLimiter(Metrics.globalRegistry, properties);
    }

    /**
     * 记录一次故障事件并基于滑动窗口判定日志输出决策。
     *
     * @param stableKey 固定小写的稳定限频标识键，不能为空
     * @param eventCode 稳定大写下划线事件编码，不能为空
     * @return 包含判定动作、抑制计数与总故障次数的 Decision 结果对象
     */
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

    /**
     * 记录依赖或故障恢复，清理滑动窗口状态。
     *
     * @param stableKey 固定小写的稳定限频标识键，不能为空
     * @return 若先前存在故障则返回包含恢复信息的 Decision，无先前故障时返回 NONE
     */
    public Decision onRecovery(String stableKey) {
        String key = requireStableKey(stableKey);
        if (!enabled) return Decision.none();
        Window previous = windows.remove(key);
        return previous == null
                ? Decision.none()
                : Decision.recovery(previous.suppressedSinceEmission(), previous.totalFailures());
    }

    /**
     * 递增特定事件码被压制忽略的次数指标。
     *
     * @param eventCode 稳定事件编码
     */
    private void incrementSuppressed(String eventCode) {
        Counter.builder(SUPPRESSED_METRIC)
                .description("Repeated failure log events suppressed before append")
                .tag("event_code", eventCode)
                .register(registry)
                .increment();
    }

    /**
     * 校验限频键是否符合小写稳定编码正则。
     *
     * @param value 待判定的限频键字符串
     * @return 校验通过的合法原值
     * @throws IllegalArgumentException 当键名不合法时抛出
     */
    private String requireStableKey(String value) {
        if (value == null || !STABLE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("log rate-limit key must be a stable code");
        }
        return value;
    }

    /**
     * 规范化事件编码，不匹配时回退为 UNCLASSIFIED。
     *
     * @param value 原始事件编码字符串
     * @return 规范化的大写大括号下划线事件编码
     */
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
        /**
         * 判定当前决策是否允许实际输出日志。
         *
         * @return true 表示为首次发生、周期汇总或故障恢复，应打印日志；false 表示被压制
         */
        public boolean shouldLog() {
            return kind == Kind.FIRST || kind == Kind.SUMMARY || kind == Kind.RECOVERY;
        }

        /**
         * 构建无需任何操作的空决策。
         *
         * @return NONE 状态的 Decision 实例
         */
        private static Decision none() {
            return new Decision(Kind.NONE, 0L, 0L);
        }

        /**
         * 构建首次故障放行决策。
         *
         * @return FIRST 状态的 Decision 实例
         */
        private static Decision first() {
            return new Decision(Kind.FIRST, 0L, 1L);
        }

        /**
         * 构建周期汇总输出决策。
         *
         * @param suppressedCount 窗口内被抑制的故障总数
         * @param totalFailures   历史累计发生故障总数
         * @return SUMMARY 状态的 Decision 实例
         */
        private static Decision summary(long suppressedCount, long totalFailures) {
            return new Decision(Kind.SUMMARY, suppressedCount, totalFailures);
        }

        /**
         * 构建故障恢复通知决策。
         *
         * @param suppressedCount 上次输出至今被抑制的故障数
         * @param totalFailures   累计发生故障总数
         * @return RECOVERY 状态的 Decision 实例
         */
        private static Decision recovery(long suppressedCount, long totalFailures) {
            return new Decision(Kind.RECOVERY, suppressedCount, totalFailures);
        }

        /**
         * 构建日志压制忽略决策。
         *
         * @param suppressedCount 当前窗口内已被抑制的次数
         * @return SUPPRESSED 状态的 Decision 实例
         */
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
