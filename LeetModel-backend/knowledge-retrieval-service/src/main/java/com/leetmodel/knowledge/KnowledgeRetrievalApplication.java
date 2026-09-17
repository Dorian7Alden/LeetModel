package com.leetmodel.knowledge;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.leetmodel",
        exclude = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class}
)
public class KnowledgeRetrievalApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeRetrievalApplication.class, args);
    }
}
