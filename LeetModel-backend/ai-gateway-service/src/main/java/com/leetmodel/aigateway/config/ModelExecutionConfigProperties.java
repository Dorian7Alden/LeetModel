package com.leetmodel.aigateway.config;

import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiResponseFormat;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 已发布后只新增版本、不原位修改的模型执行配置目录。 */
@Data
@ConfigurationProperties(prefix = "ai.gateway")
public class ModelExecutionConfigProperties {
    private Map<String, Definition> executionConfigs = new HashMap<>();

    @Data
    public static class Definition {
        private boolean enabled = true;
        private String callType;
        private String logicalModel;
        private AiProvider provider;
        private String model;
        private AiModality modality;
        private Integer maxTokens;
        private Double temperature;
        private AiResponseFormat responseFormat;
        private Boolean thinkingEnabled;
        private Integer embeddingDimension;
        private Integer maxBatchSize;
        private Integer maxInputChars;
        private Integer maxTotalChars;
        private Set<String> promptVersions = new HashSet<>();
        private Set<String> workflowVersions = new HashSet<>();
    }
}
