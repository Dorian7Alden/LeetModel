package com.leetmodel.ranking.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 排行重建 Worker 配置。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ranking.rebuild")
public class RankingRebuildProperties {
    private boolean enabled = true;
    @Min(250) private long pollDelayMs = 1000;
    @Min(60) @Max(3600) private int leaseSeconds = 300;
    @Min(1000) @Max(120000) private long heartbeatMs = 20000;

    @AssertTrue(message = "ranking.rebuild.heartbeat-ms 必须小于 lease-seconds")
    public boolean isHeartbeatBeforeLeaseExpiry() {
        return heartbeatMs < leaseSeconds * 1000L;
    }
}
