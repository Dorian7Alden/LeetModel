package com.leetmodel.gateway.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Sa-Token 响应式路由鉴权与 JWT 无状态认证配置。
 *
 * <p>在网关层建立第一道受信边界：静态放行公开白名单路径，非白名单路径校验有效 JWT 凭证。</p>
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig {

    private final ObjectMapper objectMapper;

    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    @Value("${jwt.timeout:604800}")
    private long timeout;

    /**
     * 配置 StpLogic 为 JWT 无状态签名校验模式。
     *
     * @return StpLogic 实例
     */
    @Bean
    public StpLogic stpLogic() {
        StpLogicJwtForStateless stpLogic = new StpLogicJwtForStateless("login");

        cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
        config.setJwtSecretKey(jwtSecretKey);
        config.setTimeout(timeout);
        stpLogic.setConfig(config);

        return stpLogic;
    }

    /**
     * 注册 Sa-Token 响应式全局过滤器，定义路由白名单与鉴权拦截规则。
     *
     * @return SaReactorFilter 过滤器实例
     */
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 白名单：登录、注册、Knife4j 文档无需 Token
                .addExclude(
                        "/api/auth/login",
                        "/api/auth/register",
                        // Gateway 自身健康检查无需登录
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        // 访问边界由 ManagementEndpointWebFilter 统一执行
                        "/actuator/prometheus",
                        // 公开题目浏览无需认证
                        "/api/public/problems/**",
                        // Knife4j 聚合文档页面与 API 规范端点
                        "/doc.html",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/favicon.ico",
                        // 各服务被网关路由前缀包裹后的 OpenAPI 端点
                        "/api/auth/v3/api-docs",
                        "/api/users/v3/api-docs",
                        "/api/problems/v3/api-docs",
                        "/api/teams/v3/api-docs",
                        "/api/submissions/v3/api-docs",
                        "/api/reviews/v3/api-docs",
                        "/api/rankings/v3/api-docs",
                        "/api/suggestions/v3/api-docs",
                        "/api/assistant/v3/api-docs",
                        "/api/admin/v3/api-docs"
                )
                // 浏览器 CORS 预检不携带登录态，实际业务请求仍必须登录
                .setAuth(obj -> {
                    if (!"OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                        StpUtil.checkLogin();
                    }
                })
                // 鉴权失败返回统一格式 JSON
                .setError(e -> buildUnauthorizedResponse());
    }

    private String buildUnauthorizedResponse() {
        SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=UTF-8");
        SaHolder.getResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
        try {
            return objectMapper.writeValueAsString(
                    Result.fail(40101, "未登录或 Token 已失效，请重新登录")
            );
        } catch (JsonProcessingException e) {
            return "{\"code\":40101,\"message\":\"未登录或 Token 已失效，请重新登录\",\"data\":null}";
        }
    }
}
