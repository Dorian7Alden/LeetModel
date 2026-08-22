package com.leetmodel.team;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * LeetModel 团队服务启动类。
 */
@SpringBootApplication(scanBasePackages = {
        "com.leetmodel.team",
        "com.leetmodel.common"
})
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
@MapperScan("com.leetmodel.team.mapper")
public class LeetModelTeamApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelTeamApplication.class, args);
    }
}
