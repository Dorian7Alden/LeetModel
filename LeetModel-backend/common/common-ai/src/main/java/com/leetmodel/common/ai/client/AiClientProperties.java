package com.leetmodel.common.ai.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 网关客户端配置。
 */
@Data
@ConfigurationProperties(prefix = "common.ai-client")
public class AiClientProperties {

    private boolean enabled = true;

    private String baseUrl = "http://localhost:8090";

    /** 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 读取超时：AI 生成（含思考）可能较慢，默认放宽到 5 分钟。 */
    private Duration readTimeout = Duration.ofMinutes(5);
}
