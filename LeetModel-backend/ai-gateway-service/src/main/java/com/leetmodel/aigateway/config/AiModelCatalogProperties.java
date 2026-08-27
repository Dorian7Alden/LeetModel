package com.leetmodel.aigateway.config;

import com.leetmodel.common.ai.model.AiContentType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = "ai.gateway")
public class AiModelCatalogProperties {
    private Map<String, ModelProfile> models = new HashMap<>();

    @Data
    public static class ModelProfile {
        private String displayName;
        private boolean enabled = true;
        private AiApiProtocol protocol;
        private Set<AiContentType> inputTypes = new HashSet<>();
        private boolean jsonOutput;
        private boolean thinking;
        private boolean tools;
        private Integer contextTokens;
        private Integer maxOutputTokens;
        private Integer maxImages;
        private Long maxTotalImageBytes;
        private Set<String> imageMediaTypes = new HashSet<>(Set.of(
                "image/jpeg", "image/png", "image/gif", "image/webp"
        ));
    }
}
