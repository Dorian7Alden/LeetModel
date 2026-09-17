package com.leetmodel.common.core.management;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityHealthAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityHealthAutoConfiguration.class));

    @Test
    void mustProvideStableDegradedGroupMember() {
        contextRunner.run(context -> {
            HealthIndicator indicator = context.getBean(
                    "degradedStateHealthIndicator", HealthIndicator.class);

            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        });
    }
}
