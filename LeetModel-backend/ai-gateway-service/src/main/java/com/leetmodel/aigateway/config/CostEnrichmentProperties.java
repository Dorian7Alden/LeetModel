package com.leetmodel.aigateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** 本地不可变价格快照补全配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.cost-enrichment")
public class CostEnrichmentProperties {
    private boolean enabled = true;
    @Min(1) @Max(20)
    private int maxAttempts = 3;
    @Min(1) @Max(500)
    private int batchSize = 50;
    private Duration retryDelay = Duration.ofMinutes(5);
    @Valid
    private Map<String, PriceSnapshot> snapshots = new HashMap<>();

    @Data
    public static class PriceSnapshot {
        @NotBlank
        private String version;
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}")
        private String currency;
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal inputPerMillionTokens;
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal outputPerMillionTokens;
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal cacheHitPerMillionTokens;
        @DecimalMin(value = "0.0", inclusive = true)
        private BigDecimal cacheCreationPerMillionTokens;
    }
}
