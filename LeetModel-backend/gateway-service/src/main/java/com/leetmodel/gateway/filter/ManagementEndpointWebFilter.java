package com.leetmodel.gateway.filter;

import com.leetmodel.common.core.management.ManagementAccessPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 保护 Gateway 自身的 Actuator 详情与 Prometheus 端点。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManagementEndpointWebFilter implements WebFilter {

    private final ManagementAccessPolicy accessPolicy;

    /**
     * @param managementToken 由运行环境提供的管理 Token
     */
    public ManagementEndpointWebFilter(
            @Value("${leetmodel.management.token:}") String managementToken
    ) {
        this.accessPolicy = new ManagementAccessPolicy(managementToken);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!ManagementAccessPolicy.isManagementPath(path)) return chain.filter(exchange);
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String remoteHost = remoteAddress == null || remoteAddress.getAddress() == null
                ? null : remoteAddress.getAddress().getHostAddress();
        boolean allowed = accessPolicy.isAllowed(
                path,
                remoteHost,
                exchange.getRequest().getHeaders().getFirst(ManagementAccessPolicy.TOKEN_HEADER)
        );
        if (allowed) return chain.filter(exchange);
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        return exchange.getResponse().setComplete();
    }
}
