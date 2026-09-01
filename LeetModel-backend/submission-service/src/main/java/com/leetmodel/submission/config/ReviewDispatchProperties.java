package com.leetmodel.submission.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 提交到评审链路的迁移与故障回退配置。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "submission.review")
public class ReviewDispatchProperties {

    @NotNull
    private Transport transport = Transport.MQ_PRIMARY;

    /**
     * 评审任务传输方式，任一时刻只允许一种方式推进 Outbox。
     */
    public enum Transport {
        /** Outbox Relay 发布 RocketMQ。 */
        MQ_PRIMARY,
        /** Outbox Relay 通过幂等 Feign 接口投递。 */
        FEIGN_RELAY
    }
}
