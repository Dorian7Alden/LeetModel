package com.leetmodel.common.core.logging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoggingSecurityAutoConfiguration.class));

    @Test
    void shouldCreateLimiterOnlyWhenMetricsRegistryExists() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(FailureLogLimiter.class));

        contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> assertThat(context).hasSingleBean(FailureLogLimiter.class));
    }
}
