package com.leetmodel.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import reactor.core.publisher.Hooks;

/**
 * LeetModel API 网关 —— 系统唯一对外入口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>路径匹配 → 路由转发到对应微服务</li>
 *   <li>TraceId 生成与透传：每个请求自动分配链路追踪 ID</li>
 *   <li>JWT 鉴权拦截：白名单路径放行，其余校验登录态</li>
 *   <li>跨域处理：统一 CORS，业务服务无需各自配置</li>
 * </ul>
 *
 * <p>技术栈：Spring Cloud Gateway（Netty + WebFlux，非阻塞 I/O）。</p>
 *
 * @author LeetModel
 */
@SpringBootApplication
@EnableDiscoveryClient
public class LeetModelGatewayApplication {

    public static void main(String[] args) {
        // 启用 Reactor 自动上下文传播，使 MDC 在 WebFlux 线程切换时自动传递
        // 必须在 SpringApplication.run() 之前调用，否则第一条请求可能丢失 TraceId
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(LeetModelGatewayApplication.class, args);
    }
}
