package com.leetmodel.common.core.filter;

import com.leetmodel.common.core.management.ManagementAccessPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Actuator 与 Prometheus 运维端点安全防护过滤器。
 *
 * <p>拦截 /actuator/** 路径，基于 ManagementAccessPolicy 仅允许本机内网访问或携带合法管理 Token 的请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManagementEndpointServletFilter extends OncePerRequestFilter {

    /** 运维端点访问策略控制类 */
    private final ManagementAccessPolicy accessPolicy;

    /**
     * 构造运维端点防护过滤器。
     *
     * @param managementToken 运行环境注入的管理访问令牌
     */
    public ManagementEndpointServletFilter(
            @Value("${leetmodel.management.token:}") String managementToken
    ) {
        this.accessPolicy = new ManagementAccessPolicy(managementToken);
    }

    /**
     * 判定当前请求是否需要跳过安全过滤。
     *
     * @param request 当前 HTTP 请求对象
     * @return true 表示非 /actuator 路径，跳过本过滤器
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ManagementAccessPolicy.isManagementPath(request.getRequestURI());
    }

    /**
     * 执行运维端点访问白名单与管理 Token 鉴权校验。
     *
     * @param request     当前 HTTP 请求对象
     * @param response    当前 HTTP 响应对象
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException      I/O 读写异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        boolean allowed = accessPolicy.isAllowed(
                request.getRequestURI(),
                request.getRemoteAddr(),
                request.getHeader(ManagementAccessPolicy.TOKEN_HEADER)
        );
        if (!allowed) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
