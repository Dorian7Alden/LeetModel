package com.leetmodel.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * LeetModel 用户服务 —— 负责用户注册登录、RBAC 权限管理。
 *
 * <p>关键注解：
 * <ul>
 *   <li>{@code @SpringBootApplication} — 启动 Spring Boot 自动配置</li>
 *   <li>{@code @EnableDiscoveryClient} — 注册到 Nacos</li>
 *   <li>{@code @EnableFeignClients} — 扫描 common-api 中的 Feign 接口</li>
 * </ul>
 *
 * @since 0.0.1
 */
@SpringBootApplication(scanBasePackages = "com.leetmodel")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
