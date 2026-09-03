package com.leetmodel.admin.integration;

import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.api.feign.UserFeignFallback;
import com.leetmodel.common.api.feign.SkyWalkingFeignCapability;
import com.leetmodel.common.api.feign.TraceIdFeignInterceptor;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.SkyWalkingCorrelation;
import org.apache.skywalking.apm.toolkit.trace.Trace;
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
    @Import({UserFeignFallback.class, TraceIdFeignInterceptor.class,
            SkyWalkingFeignCapability.class})
    static class TestApplication {
    }

    @jakarta.annotation.Resource
    private UserFeignClient userFeignClient;

    @Test
    @Trace(operationName = "trace-contract/feign-user-count")
    void shouldCallSpringBoot3ServiceWithOpenFeign() {
        String traceId = System.getenv().getOrDefault(
                "OBSERVABILITY_COMPATIBILITY_TRACE_ID", "feign-compatibility-trace");
        Result<Long> result;
        try (CorrelationContext.Scope ignored = CorrelationContext.open(
                CorrelationSnapshot.EMPTY.withTraceId(traceId))) {
            SkyWalkingCorrelation.bindBusinessTraceId(traceId);
            result = userFeignClient.getUserCount();
        }

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isPositive();
    }
}
