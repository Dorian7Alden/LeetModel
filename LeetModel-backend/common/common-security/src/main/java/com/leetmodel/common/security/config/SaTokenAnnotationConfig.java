package com.leetmodel.common.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.common.security.token.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 注解鉴权拦截器配置。
 *
 * <p>向 Spring MVC 注册 SaInterceptor 拦截器：先校验 Token 是否命中 Redis 黑名单，
 * 再解析各个业务微服务 Controller 上的 @SaCheckRole 与 @SaCheckPermission 注解。
 * 网关已做同样的校验，这里保留服务侧防线，避免绕过网关直连服务端口复用已登出的 Token。</p>
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenAnnotationConfig implements WebMvcConfigurer {

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 向 WebMvc 拦截器注册中心追加 Sa-Token 注解拦截器。
     *
     * @param registry Spring MVC 拦截器注册中心，不能为空
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        SaInterceptor saInterceptor = new SaInterceptor(handler -> rejectRevokedToken());
        registry.addInterceptor(saInterceptor).addPathPatterns("/**");
    }

    /**
     * 在注解鉴权之前拦截已登出的 Token。
     */
    private void rejectRevokedToken() {
        tokenBlacklistService.rejectIfRevoked(StpUtil.getTokenValue());
    }
}
