package com.leetmodel.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * LeetModel 用户服务
 * <p>
 * 负责用户注册登录、RBAC 权限管理、用户信息管理。
 * <p>
 * 关键注解：
 * <ul>
 *   <li>{@code @SpringBootApplication} — 启动 Spring Boot 自动配置</li>
 *   <li>{@code @EnableDiscoveryClient} — 触发服务注册到 Nacos，
 *       启动时本服务会向 Nacos Server 发送注册请求，Nacos 控制台可见</li>
 * </ul>
 *
 * @author LeetModel Team
 * @since 0.0.1
 */
@SpringBootApplication
@EnableDiscoveryClient
public class LeetModelUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelUserApplication.class, args);
    }
}
