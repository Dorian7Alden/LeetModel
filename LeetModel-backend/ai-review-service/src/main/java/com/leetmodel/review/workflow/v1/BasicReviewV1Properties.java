package com.leetmodel.review.workflow.v1;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "review.v1")
public class BasicReviewV1Properties {
    private int maxPages = 40;
    private float renderDpi = 110;
    private float jpegQuality = 0.78f;
    private long maxImageBytes = 8 * 1024 * 1024;
    private long maxTotalImageBytes = 32 * 1024 * 1024;
}
