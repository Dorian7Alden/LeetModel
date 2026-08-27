package com.leetmodel.aigateway.config;

import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiScene;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * AI 场景路由配置。
 */
@Data
@ConfigurationProperties(prefix = "ai.gateway")
public class AiRoutingProperties {

    private Map<AiScene, Route> routes = new EnumMap<>(AiScene.class);

    /**
     * 单个场景的供应商和模型路由。
     */
    @Data
    public static class Route {
        private AiProvider provider;
        private String model;
    }
}
