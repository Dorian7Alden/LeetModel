package com.senior.leetmodelbackend.common.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "leet-model.auth.email-code")
public class AuthCodeProperties {

    private String redisCodeKeyPrefix = "auth:email:code:";

    private String redisCooldownKeyPrefix = "auth:email:cooldown:";

    private Duration redisCodeExpire = Duration.ofMinutes(5);

    private Duration redisSendCooldown = Duration.ofSeconds(60);
}
