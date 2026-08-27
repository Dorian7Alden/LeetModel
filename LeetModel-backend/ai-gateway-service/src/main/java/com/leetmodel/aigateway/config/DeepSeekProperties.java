package com.leetmodel.aigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 官方 API 配置。
 */
@ConfigurationProperties(prefix = "ai.providers.deepseek")
public class DeepSeekProperties extends AiProviderDefinitionProperties {

    public DeepSeekProperties() {
        setProviderId("deepseek");
        setDisplayName("DeepSeek");
        setBaseUrl("https://api.deepseek.com");
        setSecretRef("env:DEEPSEEK_API_KEY");
    }
}
