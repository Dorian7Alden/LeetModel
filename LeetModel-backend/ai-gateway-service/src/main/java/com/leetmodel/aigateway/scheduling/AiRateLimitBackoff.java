package com.leetmodel.aigateway.scheduling;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** new-api 最终 429 的全局有界派发退避；不重试已失败调用。 */
@Component
public class AiRateLimitBackoff {
    private static final Duration MAX_DELAY = Duration.ofSeconds(30);
    private final Clock clock;
    private int consecutiveRateLimits;
    private Instant nextDispatchAt = Instant.EPOCH;

    public AiRateLimitBackoff() {
        this(Clock.systemUTC());
    }

    AiRateLimitBackoff(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean allowDispatch() {
        return !clock.instant().isBefore(nextDispatchAt);
    }

    public synchronized Duration onRateLimited(Duration retryAfter) {
        consecutiveRateLimits = Math.min(6, consecutiveRateLimits + 1);
        Duration exponential = Duration.ofSeconds(1L << (consecutiveRateLimits - 1));
        Duration requested = retryAfter == null ? exponential : retryAfter;
        Duration delay = requested.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : requested;
        if (delay.isNegative() || delay.isZero()) delay = Duration.ofSeconds(1);
        nextDispatchAt = clock.instant().plus(delay);
        return delay;
    }

    public synchronized void onSuccess() {
        if (consecutiveRateLimits == 0) return;
        consecutiveRateLimits--;
        if (consecutiveRateLimits == 0) {
            nextDispatchAt = clock.instant();
        } else {
            nextDispatchAt = clock.instant().plusMillis(250L << (consecutiveRateLimits - 1));
        }
    }

    public synchronized BackoffSnapshot snapshot() {
        return new BackoffSnapshot(consecutiveRateLimits, nextDispatchAt);
    }

    public record BackoffSnapshot(int consecutiveRateLimits, Instant nextDispatchAt) {}
}
