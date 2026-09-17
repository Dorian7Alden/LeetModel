package com.leetmodel.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 全局跨域响应式 WebFilter 配置。
 */
@Configuration
public class CorsConfig {

    /**
     * 装配 WebFlux 全局跨域过滤器。
     *
     * @return CorsWebFilter 实例
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许所有来源（开发阶段；生产环境应限制具体域名）
        config.addAllowedOriginPattern("*");
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 允许所有请求头
        config.addAllowedHeader("*");
        // 允许携带 Cookie/Authorization 头
        config.setAllowCredentials(true);
        // 预检请求缓存 1 小时
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
