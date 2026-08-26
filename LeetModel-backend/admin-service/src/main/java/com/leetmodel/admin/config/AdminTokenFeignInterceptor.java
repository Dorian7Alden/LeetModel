package com.leetmodel.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 仅将当前管理请求的 Sa-Token 透传给仍需二次鉴权的领域管理接口。 */
@Configuration
public class AdminTokenFeignInterceptor {

    @Bean
    public RequestInterceptor adminTokenRequestInterceptor() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            copyHeader(request, template, "satoken");
            copyHeader(request, template, "Authorization");
        };
    }

    private void copyHeader(HttpServletRequest request, RequestTemplate template, String name) {
        String value = request.getHeader(name);
        if (value != null && !value.isBlank()) template.header(name, value);
    }
}
