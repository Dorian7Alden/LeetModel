package com.leetmodel.leetmodelproblem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class LeetModelProblemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelProblemApplication.class, args);
    }

}
