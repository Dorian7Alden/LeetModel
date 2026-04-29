package leet.model.leetmodelbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot 启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LeetModelBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeetModelBackendApplication.class, args);
    }

}
