package com.leetmodel.common.security.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 核心配置类。
 *
 * <p>配置 JWT 无状态模式（StpLogicJwtForStateless），不持久化 Session 到 Redis；
 * 配合 Redis 黑名单机制支持主动失效。类名加 Security 前缀防止与框架同名配置类冲突。</p>
 */
@Configuration
public class SecuritySaTokenConfig {

    /** JWT 签名秘钥 */
    @Value("${jwt.secret-key:leetmodel-default-secret-key}")
    private String jwtSecretKey;

    /** Token 有效期秒数，默认 7 天 */
    @Value("${jwt.timeout:604800}")
    private long timeout;

    /**
     * 构造无状态 JWT 鉴权逻辑驱动器 Bean。
     *
     * @return 注入了秘钥与超时配置的 StpLogic 实例
     */
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
