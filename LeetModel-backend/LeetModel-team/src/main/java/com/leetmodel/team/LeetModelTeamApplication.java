package com.leetmodel.team;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LeetModel 团队服务启动类。
 *
 * @author LeetModel
 */
@SpringBootApplication(scanBasePackages = {
        "com.leetmodel.team",
        "com.leetmodel.common"
})
@MapperScan("com.leetmodel.team.mapper")
public class LeetModelTeamApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelTeamApplication.class, args);
    }
}
