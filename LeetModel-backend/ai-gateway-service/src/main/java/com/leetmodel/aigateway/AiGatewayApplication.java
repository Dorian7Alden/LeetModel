package com.leetmodel.aigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 网关服务启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.leetmodel")
public class AiGatewayApplication {

    /**
     * 启动 AI 网关服务。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
