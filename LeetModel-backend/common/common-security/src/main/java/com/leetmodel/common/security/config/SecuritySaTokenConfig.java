package com.leetmodel.common.security.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 核心配置 —— JWT 无状态模式 + Redis 黑名单。
 *
 * <p>认证架构说明：
 * <ul>
 *   <li>Token 采用无状态 JWT 签发，不在 Redis 存储 Session（轻量、高性能）</li>
 *   <li>Redis 仅存储黑名单 —— 登出/踢人时将 Token 加入黑名单，解决 JWT 无法主动失效的固有问题</li>
 *   <li>JWT Secret Key 通过 Nacos 配置中心下发，不写死在代码中</li>
 * </ul>
 * </p>
 *
 * <p>注意：类名加 Security 前缀避免与 cn.dev33.satoken.config.SaTokenConfig 冲突。</p>
 */
@Configuration
public class SecuritySaTokenConfig {

    @Value("${jwt.secret-key:leetmodel-default-secret-key}")
    private String jwtSecretKey;

    @Value("${jwt.timeout:604800}")
    private long timeout; // 默认 7 天（秒）

    @Bean
    public StpLogic stpLogic() {
        // StpLogicJwtForStateless(String) — 构造参数是 loginType，不是 JWT 密钥
        StpLogicJwtForStateless stpLogic = new StpLogicJwtForStateless("login");

        // 配置：密钥、超时、登录策略
        cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
        config.setJwtSecretKey(jwtSecretKey);  // ← 密钥必须设到 config 里，jwtSecretKey() 从这里读
        config.setTimeout(timeout);
        config.setIsConcurrent(false);  // 同一账号只能在一处登录
        config.setIsLog(true);          // 开启登录日志
        stpLogic.setConfig(config);

        return stpLogic;
    }
}
