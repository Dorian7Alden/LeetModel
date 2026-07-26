package com.leetmodel.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * LeetModel API 网关 —— 系统唯一对外入口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>路径匹配 → 路由转发到对应微服务</li>
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
        SpringApplication.run(LeetModelGatewayApplication.class, args);
    }
}
