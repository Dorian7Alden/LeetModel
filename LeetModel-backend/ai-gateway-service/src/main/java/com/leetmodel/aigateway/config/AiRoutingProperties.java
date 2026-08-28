package com.leetmodel.aigateway.config;

import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiModality;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * AI 输入模态路由配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.gateway")
public class AiRoutingProperties {

    private Map<AiModality, Route> routes = new EnumMap<>(AiModality.class);

    /**
     * 单种输入模态的供应商和模型路由。
     */
    @Data
    public static class Route {
        private AiProvider provider;
        private String model;
    }
}
