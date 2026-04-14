package com.senior.leetmodelbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 火山引擎 Ark AI 配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ark.ai")
public class ArkAiConfig {
    
    /**
     * API 基础 URL
     */
    private String baseUrl;

    /**
     * 使用的模型名称/Endpoint ID
     */
    private String model;
}
