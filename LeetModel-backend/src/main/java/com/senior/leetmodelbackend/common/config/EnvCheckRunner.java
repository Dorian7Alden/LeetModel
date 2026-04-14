package com.senior.leetmodelbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EnvCheckRunner implements CommandLineRunner {

    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;
    private final String mailUsername;
    private final String mailPassword;
    private final String redisHost;
    private final String redisPassword;
    private final String arkApiKey;
    private final String deepseekApiKey;

    public EnvCheckRunner(
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${spring.datasource.username}") String datasourceUsername,
            @Value("${spring.datasource.password}") String datasourcePassword,
            @Value("${spring.mail.username}") String mailUsername,
            @Value("${spring.mail.password}") String mailPassword,
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.password}") String redisPassword,
            @Value("${ark.ai.api-key}") String arkApiKey,
            @Value("${spring.ai.openai.api-key}") String deepseekApiKey
    ) {
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.redisHost = redisHost;
        this.redisPassword = redisPassword;
        this.arkApiKey = arkApiKey;
        this.deepseekApiKey = deepseekApiKey;
    }


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
        
        // 检查 DeepSeek 配置
        if (!checkConfig("DeepSeek API Key", deepseekApiKey)) hasError = true;
        
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