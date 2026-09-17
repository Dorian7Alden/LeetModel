package com.leetmodel.evaluation;

import com.leetmodel.evaluation.config.EvaluationScaleProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.leetmodel")
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
@EnableScheduling
@EnableConfigurationProperties(EvaluationScaleProperties.class)
@MapperScan("com.leetmodel.evaluation.mapper")
public class AiEvaluationApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEvaluationApplication.class, args);
    }
}
