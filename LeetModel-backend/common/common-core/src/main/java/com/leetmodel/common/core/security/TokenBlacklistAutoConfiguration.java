package com.leetmodel.common.core.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Token 黑名单配置装配。
 *
 * <p>以自动配置方式注册配置属性，使网关与业务服务无需各自声明即可共享同一份
 * 键前缀、TTL 上限与降级策略。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(TokenBlacklistProperties.class)
public class TokenBlacklistAutoConfiguration {
}
