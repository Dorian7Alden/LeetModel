package com.leetmodel.evaluation.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 评价批次的服务端硬限制，防止后台实验无限放大。 */
@Data
@Validated
@ConfigurationProperties(prefix = "evaluation.limits")
public class EvaluationScaleProperties {
    @Min(1)
    private int maxSamples = 100;
    @Min(1)
    private int maxVersions = 8;
    @Min(1)
    private int maxRepeatCount = 20;
    @Min(1)
    private long maxTotalSlots = 2000;
    @Min(1)
    private long maxEstimatedCalls = 3000;
}
