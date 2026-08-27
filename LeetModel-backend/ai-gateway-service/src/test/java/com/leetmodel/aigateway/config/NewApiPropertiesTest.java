package com.leetmodel.aigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NewApiPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldBindRelayConfiguration() {
        runner.withPropertyValues(
                        "ai.new-api.base-url=http://new-api:3000/v1",
                        "ai.new-api.relay-token=test-relay-token",
                        "ai.new-api.connect-timeout=3s",
                        "ai.new-api.read-timeout=45s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    NewApiProperties properties = context.getBean(NewApiProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("http://new-api:3000/v1");
                    assertThat(properties.getRelayToken()).isEqualTo("test-relay-token");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(45));
                });
    }

    @Test
    void shouldFailClearlyWhenRelayTokenIsMissing() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure());
            assertThat(rootCause.getMessage()).contains("NEW_API_RELAY_TOKEN");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NewApiProperties.class)
    static class TestConfiguration {
    }
}
