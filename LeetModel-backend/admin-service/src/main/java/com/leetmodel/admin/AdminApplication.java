package com.leetmodel.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * LeetModel 管理后台服务启动类。
 *
 * <p>此服务通过 Feign 调用其他微服务获取数据，不连接业务数据库。</p>
 */
@SpringBootApplication(scanBasePackages = {
        "com.leetmodel.admin",
        "com.leetmodel.common"
})
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
