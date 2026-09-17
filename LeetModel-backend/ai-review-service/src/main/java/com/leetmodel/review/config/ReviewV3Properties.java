package com.leetmodel.review.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI评审 V3 专用配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "review.v3")
public class ReviewV3Properties {
    private int subTaskCorePoolSize = 16;
    private int subTaskMaxPoolSize = 32;
    private int subTaskQueueCapacity = 100;
    private int subTaskTimeoutSeconds = 75;
    private int phase2TimeoutSeconds = 120;
}
