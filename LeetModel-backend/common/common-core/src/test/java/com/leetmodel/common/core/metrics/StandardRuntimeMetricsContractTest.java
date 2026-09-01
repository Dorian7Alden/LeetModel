package com.leetmodel.common.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StandardRuntimeMetricsContractTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.application.name=runtime-metric-contract-test",
                "spring.config.import=classpath:leetmodel-observability.yml"
        })
@AutoConfigureObservability
class StandardRuntimeMetricsContractTest {

    @jakarta.annotation.Resource
    private MeterRegistry registry;

    @Test
    void exposesJvmAndNamedTaskExecutorMetrics() {
        assertThat(registry.find("jvm.memory.used").gauge()).isNotNull();
        assertThat(registry.find("executor.active")
                .tag("name", "metricContractExecutor").gauge()).isNotNull();
        assertThat(registry.find("executor.queued")
                .tag("name", "metricContractExecutor").gauge()).isNotNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    static class TestApplication {
        @Bean
        ThreadPoolTaskExecutor metricContractExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(4);
            executor.setThreadNamePrefix("metric-contract-");
            return executor;
        }
    }
}
