package com.leetmodel.aigateway.config;

import com.leetmodel.common.ai.model.AiProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/** Embedding 逻辑模型到受治理物理模型的绑定。 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.gateway")
public class AiEmbeddingProperties {
    @Valid
    private Map<String, Binding> embeddingModels = new HashMap<>();

    @Data
    public static class Binding {
        private boolean enabled = true;
        @NotNull private AiProvider provider;
        @NotBlank private String model;
        @Min(1) @Max(32768) private int dimension;
        @Min(1) @Max(128) private int maxBatchSize = 32;
        @Min(1) @Max(32768) private int maxInputChars = 8192;
        @Min(1) private int maxTotalChars = 65536;
    }
}
