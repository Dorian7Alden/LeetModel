package leet.model.leetmodelbackend.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 邮箱验证码相关配置，映射 leet-model.auth.email-code 前缀。
 */
@Data
@ConfigurationProperties(prefix = "leet-model.auth.email-code")
public class AuthCodeProperties {

    /** Redis 中邮箱验证码 key 前缀 */
    private String redisCodeKeyPrefix = "auth:email:code:";

    /** Redis 中发送冷却 key 前缀 */
    private String redisCooldownKeyPrefix = "auth:email:cooldown:";

    /** 邮箱验证码在 Redis 中的过期时间 */
    private Duration redisCodeExpire = Duration.ofMinutes(5);

    /** 邮箱验证码发送冷却时间 */
    private Duration redisSendCooldown = Duration.ofSeconds(60);
}