package com.leetmodel.review.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 正式评审领域 Worker 的并发、租约和心跳配置。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "review.worker")
public class ReviewWorkerProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(8)
    private int concurrency = 2;

    @Min(30)
    @Max(900)
    private long leaseSeconds = 120;

    @Min(1000)
    @Max(120000)
    private long heartbeatMs = 20000;

    @Min(1)
    @Max(10)
    private int maxAttempts = 3;

    /**
     * 心跳必须早于租约过期，否则健康实例也会被并发接管。
     *
     * @return 配置是否安全
     */
    @AssertTrue(message = "review.worker.heartbeat-ms 必须小于 lease-seconds")
    public boolean isHeartbeatBeforeLeaseExpiry() {
        return heartbeatMs < leaseSeconds * 1000;
    }
}
