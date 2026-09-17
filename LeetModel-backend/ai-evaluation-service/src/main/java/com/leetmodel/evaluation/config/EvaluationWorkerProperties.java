package com.leetmodel.evaluation.config;

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
@ConfigurationProperties(prefix = "evaluation.worker")
public class EvaluationWorkerProperties {
    private boolean enabled = true;

    @Min(1) @Max(1)
    private int concurrency = 1;

    @Min(30) @Max(900)
    private long leaseSeconds = 120;

    @Min(1000) @Max(120000)
    private long heartbeatMs = 20000;

    @Min(16) @Max(4096)
    private int signalCapacity = 512;

    @Min(10000) @Max(600000)
    private long reconciliationDelayMs = 30000;

    @Min(1) @Max(500)
    private int reconciliationBatchSize = 100;

    @Min(1) @Max(100)
    private int onlineWarningCount = 20;

    @Min(1000) @Max(600000)
    private long onlineWarningWaitMs = 30000;

    @Min(1000) @Max(60000)
    private long pressureCacheMs = 5000;

    @AssertTrue(message = "evaluation.worker.heartbeat-ms 必须小于 lease-seconds")
    public boolean isHeartbeatBeforeLeaseExpiry() {
        return heartbeatMs < leaseSeconds * 1000;
    }
}
