package com.leetmodel.aigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kimi 官方 API 配置。
 */
@ConfigurationProperties(prefix = "ai.providers.kimi")
public class KimiProperties extends AiProviderDefinitionProperties {

    public KimiProperties() {
        setProviderId("kimi");
        setDisplayName("Kimi");
        setBaseUrl("https://api.moonshot.cn/v1");
        setSecretRef("env:KIMI_API_KEY");
    }
}
