package com.leetmodel.common.ai.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * AI 网关客户端自动配置。
 */
@Configuration
@EnableConfigurationProperties(AiClientProperties.class)
@ConditionalOnProperty(
        prefix = "common.ai-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiClientConfiguration {

    /**
     * 创建统一 AI 客户端。
     *
     * @param builder RestClient 构建器
     * @param properties 客户端配置
     * @return AI 客户端
     */
    @Bean
    @ConditionalOnMissingBean(AiClient.class)
    public AiClient aiClient(RestClient.Builder builder, AiClientProperties properties) {
        RestClient restClient = builder
                .baseUrl(properties.getBaseUrl())
                .build();
        return new HttpAiClient(restClient);
    }
}
