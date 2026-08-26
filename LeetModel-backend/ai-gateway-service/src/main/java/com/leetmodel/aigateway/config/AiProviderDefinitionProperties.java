package com.leetmodel.aigateway.config;

import lombok.Data;

import java.time.Duration;

/**
 * 协议无关的供应商维护字段。
 *
 * <p>品牌配置只补充运行参数；Provider ID、显示名称、协议和密钥引用保持同一结构。</p>
 */
@Data
public class AiProviderDefinitionProperties {
    private String providerId;
    private String displayName;
    private String baseUrl;
    private AiApiProtocol protocol = AiApiProtocol.OPENAI_COMPLETIONS;
    private String secretRef;
    private String apiKey;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofMinutes(10);
}
