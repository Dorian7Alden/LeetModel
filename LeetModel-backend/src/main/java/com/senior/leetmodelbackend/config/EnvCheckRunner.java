package com.senior.leetmodelbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EnvCheckRunner implements CommandLineRunner {

    @Value("spring.datasource.url")
    private String datasourceUrl;

    @Value("spring.datasource.username")
    private String datasourceUsername;

    @Value("spring.datasource.password:")
    private String datasourcePassword;

    @Value("spring.mail.username:")
    private String mailUsername;

    @Value("spring.mail.password:")
    private String mailPassword;

    @Value("spring.data.redis.host:")
    private String redisHost;

    @Value("spring.data.redis.password:")
    private String redisPassword;

    @Value("ark.ai.api-key:")
    private String arkApiKey;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== 开始检查环境配置 ==========");
        
        boolean hasError = false;
        
        // 检查数据库配置
        if (!checkConfig("MySQL URL", datasourceUrl)) hasError = true;
        if (!checkConfig("MySQL 用户名", datasourceUsername)) hasError = true;
        if (!checkConfig("MySQL 密码", datasourcePassword)) hasError = true;
        
        // 检查邮件配置
        if (!checkConfig("QQ 邮箱用户名", mailUsername)) hasError = true;
        if (!checkConfig("QQ 邮箱授权码", mailPassword)) hasError = true;
        
        // 检查 Redis 配置
        if (!checkConfig("Redis Host", redisHost)) hasError = true;
        if (!checkConfig("Redis 密码", redisPassword)) hasError = true;
        
        // 检查火山引擎配置
        if (!checkConfig("Ark API Key", arkApiKey)) hasError = true;
        
        if (hasError) {
            log.error("========== 环境配置检查失败，请检查上述配置项 ==========");
            throw new RuntimeException("环境配置检查失败，请检查控制台错误信息");
        } else {
            log.info("========== 环境配置检查通过 ==========");
        }
    }
    
    /**
     * 检查配置项是否为空
     * @param configName 配置项名称
     * @param configValue 配置项值
     * @return true-配置有效，false-配置为空
     */
    private boolean checkConfig(String configName, String configValue) {
        if (configValue == null || configValue.trim().isEmpty() || "未配置".equals(configValue)) {
            log.error("❌ [必填配置缺失] {}: 未配置或为空", configName);
            return false;
        }
        log.info("✅ [配置检查通过] {}: ***", configName);
        return true;
    }
}