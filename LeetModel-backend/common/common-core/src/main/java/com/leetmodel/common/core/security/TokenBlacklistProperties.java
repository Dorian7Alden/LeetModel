package com.leetmodel.common.core.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token 黑名单配置。
 *
 * <p>网关与业务服务共享同一份键规则与降级策略，存储位置为安全状态 Redis，
 * 与业务缓存实例物理隔离。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auth.token-blacklist")
public class TokenBlacklistProperties {

    /** 是否启用黑名单校验；关闭后只保留本地登出语义。 */
    private boolean enabled = true;

    /** 黑名单键前缀，最终键为该前缀拼接 Token 指纹。 */
    private String keyPrefix = "leetmodel:auth:blacklist:";

    /** Redis 不可用时是否拒绝请求；false 表示放行并累加降级计数。 */
    private boolean failClosed = false;

    /** 单条黑名单记录的 TTL 上限，默认与 JWT 有效期一致（7 天）。 */
    private long maxTtlSeconds = 604800L;

    /** 黑名单 Redis 命令与连接超时，毫秒；Redis 不可用时按该上限快速进入降级。 */
    private long redisTimeoutMs = 300L;
}
