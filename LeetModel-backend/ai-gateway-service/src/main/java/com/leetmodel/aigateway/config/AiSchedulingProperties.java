package com.leetmodel.aigateway.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "ai.scheduling")
public class AiSchedulingProperties {
    private boolean enabled;
    @Min(1) @Max(32)
    private int concurrency = 4;
    @Min(0) @Max(31)
    private int reservedP0Concurrency = 1;
    @Min(1) @Max(10000)
    private int maxQueueSize = 500;
    @Min(0) @Max(9999)
    private int reservedP0QueueSize = 50;
    private Duration leaseDuration = Duration.ofSeconds(15);
    private Duration heartbeatInterval = Duration.ofSeconds(5);
    private Duration resultRetention = Duration.ofHours(24);
}
