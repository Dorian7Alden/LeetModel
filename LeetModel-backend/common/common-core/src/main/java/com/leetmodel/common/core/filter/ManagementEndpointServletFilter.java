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
 * 保护 Servlet 服务的 Actuator 详情与 Prometheus 端点。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManagementEndpointServletFilter extends OncePerRequestFilter {

    private final ManagementAccessPolicy accessPolicy;

    /**
     * @param managementToken 由运行环境提供的管理 Token
     */
    public ManagementEndpointServletFilter(
            @Value("${leetmodel.management.token:}") String managementToken
    ) {
        this.accessPolicy = new ManagementAccessPolicy(managementToken);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ManagementAccessPolicy.isManagementPath(request.getRequestURI());
    }

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
