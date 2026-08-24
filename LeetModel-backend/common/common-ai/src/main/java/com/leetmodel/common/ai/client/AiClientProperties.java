package com.leetmodel.common.ai.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 网关客户端配置。
 */
@Data
@ConfigurationProperties(prefix = "common.ai-client")
public class AiClientProperties {

    private boolean enabled = true;

    private String baseUrl = "http://localhost:8090";
}
