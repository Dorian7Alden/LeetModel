package com.leetmodel.problem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.leetmodel")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
public class LeetModelProblemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelProblemApplication.class, args);
    }

}
