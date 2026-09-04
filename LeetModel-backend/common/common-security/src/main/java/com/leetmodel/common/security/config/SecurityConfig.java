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
 * <p>禁用 CSRF、Session 与默认 HTTP Basic/表单登录，将全部请求放行交由 Sa-Token 进行统一拦截与鉴权，
 * 避免两套安全上下文相互冲突产生空响应体 403 错误。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置无状态 Spring Security 过滤链。
     *
     * @param http HttpSecurity 构建器对象
     * @return 构建完毕的 SecurityFilterChain 实例
     * @throws Exception 配置异常
     */
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
