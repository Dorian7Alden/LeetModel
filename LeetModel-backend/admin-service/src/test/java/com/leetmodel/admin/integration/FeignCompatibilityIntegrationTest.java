package com.leetmodel.admin.integration;

import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.api.feign.UserFeignFallback;
import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OBSERVABILITY_COMPATIBILITY", matches = "true")
@SpringBootTest(
        classes = FeignCompatibilityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.openfeign.client.config.user-service.url=http://127.0.0.1:18081",
                "spring.cloud.openfeign.client.config.userFeignClient.url=http://127.0.0.1:18081"
        }
)
class FeignCompatibilityIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = UserFeignClient.class)
    @Import(UserFeignFallback.class)
    static class TestApplication {
    }

    @jakarta.annotation.Resource
    private UserFeignClient userFeignClient;

    @Test
    void shouldCallSpringBoot3ServiceWithOpenFeign() {
        Result<Long> result = userFeignClient.getUserCount();

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isPositive();
    }
}
