package com.leetmodel.common.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置 —— 与 Sa-Token 协同工作。
 *
 * <p>职责分工：
 * <ul>
 *   <li>gateway 网关：认证（JWT 验签 + Redis 黑名单校验）</li>
 *   <li>业务服务 + common-security：鉴权（@SaCheckRole / @SaCheckPermission）</li>
 *   <li>SecurityConfig：关闭 CSRF + Session，放行公开资源（Knife4j 等）</li>
 * </ul>
 * </p>
 *
 * @author LeetModel
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

                // 放行公开接口（登录注册、API 文档）
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/public/problems/**",
                                "/api/auth/v3/api-docs",
                                "/api/users/v3/api-docs",
                                "/api/problems/v3/api-docs",
                                "/api/teams/v3/api-docs",
                                "/api/admin/dashboard/v3/api-docs",
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // 无状态会话，每个请求独立认证
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
