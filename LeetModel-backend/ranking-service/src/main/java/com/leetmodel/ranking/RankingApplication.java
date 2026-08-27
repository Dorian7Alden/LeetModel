package com.leetmodel.ranking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.leetmodel")
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
@MapperScan("com.leetmodel.ranking.mapper")
public class RankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RankingApplication.class, args);
    }
}
