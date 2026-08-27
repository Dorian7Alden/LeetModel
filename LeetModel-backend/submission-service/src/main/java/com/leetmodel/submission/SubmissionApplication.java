package com.leetmodel.submission;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.leetmodel")
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
@EnableScheduling
@MapperScan("com.leetmodel.submission.mapper")
public class SubmissionApplication {
    public static void main(String[] args) { SpringApplication.run(SubmissionApplication.class, args); }
}
