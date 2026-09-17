package com.leetmodel.common.messaging.config;

import com.leetmodel.common.messaging.MessageCodec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 可靠消息公共配置。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "leetmodel.messaging")
public class MessagingProperties {

    private boolean enabled;

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String namespace = "lm-dev";

    @Min(1024)
    @Max(MessageCodec.MAX_PAYLOAD_BYTES)
    private int maxPayloadBytes = MessageCodec.MAX_PAYLOAD_BYTES;

    @Min(100)
    @Max(30000)
    private long sendTimeoutMs = 3000;

    @Valid
    private Relay relay = new Relay();

    /**
     * Outbox Relay 配置。
     */
    @Getter
    @Setter
    public static class Relay {

        private boolean enabled = true;

        @Min(1)
        @Max(500)
        private int batchSize = 50;

        @Min(100)
        @Max(60000)
        private long intervalMs = 1000;

        @Min(5)
        @Max(300)
        private long leaseSeconds = 30;
    }
}
