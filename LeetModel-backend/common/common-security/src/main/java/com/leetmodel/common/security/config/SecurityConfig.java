package com.leetmodel.common.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 基础配置。
 *
 * <p>职责分工：
 * <ul>
 *   <li>Gateway 使用 Sa-Token 完成 JWT 登录认证</li>
 *   <li>业务服务使用 Sa-Token 注解完成角色和权限鉴权</li>
 *   <li>Spring Security 仅关闭不需要的默认机制，不重复维护认证状态</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT 无状态，不需要 CSRF 保护
                .csrf(AbstractHttpConfigurer::disable)

                // 关闭 HTTP Basic 和 Form 登录（JWT 模式下不需要，否则浏览器弹登录框）
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // Sa-Token 负责认证鉴权，Spring Security 不重复拦截请求
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                // 无状态会话，每个请求独立认证
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
