package com.leetmodel.assistant.rag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 客服 RAG V1 的集中配置。默认关闭，避免配置升级改变既有客服行为。 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "assistant.rag")
public class RagProperties {

    private boolean enabled;

    @NotBlank
    private String knowledgeBasePath = "../rag_kb";

    @NotBlank
    @Pattern(regexp = "[a-z0-9][a-z0-9_-]{2,254}")
    private String indexAlias = "leetmodel-rag-v1-read";

    @Min(1)
    @Max(100)
    private int topK = 8;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double scoreThreshold = 0.65;

    @Min(128)
    @Max(32768)
    private int tokenBudget = 3000;

    @Min(1)
    @Max(128)
    private int embeddingBatchSize = 16;

    @Min(1)
    private int chunkMinTokens = 80;

    @Min(1)
    private int chunkTargetTokens = 320;

    @Min(1)
    private int chunkMaxTokens = 480;

    @Min(0)
    private int chunkOverlapTokens = 48;

    @Min(1)
    @Max(32768)
    private int maxEmbeddingInputChars = 8192;

    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(5);

    @NotNull
    private StoreType storeType = StoreType.ELASTICSEARCH;

    @AssertTrue(message = "requestTimeout must be greater than zero")
    public boolean isRequestTimeoutValid() {
        return requestTimeout != null && !requestTimeout.isZero() && !requestTimeout.isNegative();
    }

    @AssertTrue(message = "chunk token limits must satisfy overlap < min <= target <= max")
    public boolean isChunkPolicyValid() {
        return chunkOverlapTokens < chunkMinTokens
                && chunkMinTokens <= chunkTargetTokens
                && chunkTargetTokens <= chunkMaxTokens;
    }

    public enum StoreType {
        ELASTICSEARCH,
        IN_MEMORY
    }
}
