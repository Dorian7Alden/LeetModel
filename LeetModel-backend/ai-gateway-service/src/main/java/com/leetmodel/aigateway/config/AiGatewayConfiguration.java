package com.leetmodel.aigateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 网关配置属性入口。
 */
@Configuration
@EnableConfigurationProperties({
        DeepSeekProperties.class,
        KimiProperties.class,
        AiRoutingProperties.class,
        AiModelCatalogProperties.class
})
public class AiGatewayConfiguration {
}
