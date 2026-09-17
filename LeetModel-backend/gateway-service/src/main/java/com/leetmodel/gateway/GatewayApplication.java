package com.leetmodel.gateway;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import io.micrometer.context.ContextRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import reactor.core.publisher.Hooks;

/**
 * LeetModel API 网关：系统唯一对外入口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>路径匹配并路由转发到下游微服务</li>
 *   <li>TraceId 生成与透传，建立全链路追踪</li>
 *   <li>基于 Sa-Token 执行 JWT 统一鉴权拦截</li>
 *   <li>全局 CORS 跨域治理</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        // 启用 Reactor 自动上下文传播，使 MDC 在 WebFlux 线程切换时自动传递
        // 必须在 SpringApplication.run() 之前调用，否则第一条请求可能丢失 TraceId
        configureContextPropagation();
        SpringApplication.run(GatewayApplication.class, args);
    }

    static void configureContextPropagation() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(CorrelationContext.REACTOR_CONTEXT_KEY);
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                CorrelationContext.REACTOR_CONTEXT_KEY,
                CorrelationContext::capture,
                CorrelationContext::replace,
                CorrelationContext::clear
        );
        Hooks.enableAutomaticContextPropagation();
    }
}
