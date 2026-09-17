package com.leetmodel.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 操作审计归档服务入口。 */
@SpringBootApplication(scanBasePackages = "com.leetmodel.audit")
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}
