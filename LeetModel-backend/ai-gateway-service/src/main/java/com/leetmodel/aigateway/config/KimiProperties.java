package com.leetmodel.aigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Kimi 官方 API 配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.providers.kimi")
public class KimiProperties {

    private String baseUrl = "https://api.moonshot.cn/v1";
    private String apiKey;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofMinutes(10);
}
