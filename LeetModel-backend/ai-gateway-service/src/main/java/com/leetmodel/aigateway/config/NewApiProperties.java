package com.leetmodel.aigateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** new-api Relay API 的安全运行配置。 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.new-api")
public class NewApiProperties {

    @NotBlank(message = "ai.new-api.base-url 不能为空")
    private String baseUrl = "http://127.0.0.1:3000/v1";

    @NotBlank(message = "缺少 NEW_API_RELAY_TOKEN，无法启动 AI 网关")
    private String relayToken;

    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofMinutes(10);
}
