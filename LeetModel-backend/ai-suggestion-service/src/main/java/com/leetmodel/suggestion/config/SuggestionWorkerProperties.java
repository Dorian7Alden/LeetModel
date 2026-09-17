package com.leetmodel.suggestion.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "suggestion.worker")
public class SuggestionWorkerProperties {
    private boolean enabled = true;

    @Min(1) @Max(1)
    private int concurrency = 1;

    @Min(30) @Max(900)
    private long leaseSeconds = 120;

    @Min(1000) @Max(120000)
    private long heartbeatMs = 20000;

    @Min(1) @Max(10)
    private int maxAttempts = 3;

    @Min(16) @Max(4096)
    private int signalCapacity = 256;

    @Min(10000) @Max(600000)
    private long reconciliationDelayMs = 30000;

    @Min(1) @Max(500)
    private int reconciliationBatchSize = 50;

    @Min(10) @Max(100000)
    private int severeBacklogCount = 1000;

    @Min(60) @Max(86400)
    private long severeOldestWaitingSeconds = 600;

    @AssertTrue(message = "suggestion.worker.heartbeat-ms 必须小于 lease-seconds")
    public boolean isHeartbeatBeforeLeaseExpiry() {
        return heartbeatMs < leaseSeconds * 1000;
    }
}
