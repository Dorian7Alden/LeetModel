package com.leetmodel.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置。
 *
 * <p>使用 BCrypt 加密算法自动加盐哈希，抗彩虹表攻击。</p>
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 创建 BCrypt 密码编码器 Bean。
     *
     * @return PasswordEncoder 密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
