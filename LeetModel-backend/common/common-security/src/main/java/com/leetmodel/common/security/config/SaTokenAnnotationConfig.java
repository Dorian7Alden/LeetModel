package com.leetmodel.common.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 注解鉴权拦截器配置。
 *
 * <p>向 Spring MVC 注册 SaInterceptor 拦截器，确保各个业务微服务 Controller 上的
 * @SaCheckRole 与 @SaCheckPermission 注解能够被有效解析与拦截。</p>
 */
@Configuration
public class SaTokenAnnotationConfig implements WebMvcConfigurer {

    /**
     * 向 WebMvc 拦截器注册中心追加 Sa-Token 注解拦截器。
     *
     * @param registry Spring MVC 拦截器注册中心，不能为空
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
