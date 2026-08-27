package com.leetmodel.common.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 启用 Servlet 微服务中的 Sa-Token 注解鉴权。
 * Gateway 登录校验不能替代服务自身对角色和权限注解的执行。
 */
@Configuration
public class SaTokenAnnotationConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
