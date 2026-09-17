package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {

    private final PropertySource<?> properties = loadProperties();

    @Test
    void mustExposeOnlyOperationalEndpointsAndEnableProbes() {
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .isEqualTo(true);
        assertThat(properties.getProperty("management.endpoint.health.show-details"))
                .isEqualTo("never");
    }

    @Test
    void livenessMustExcludeRecoverableDependencies() {
        assertThat(properties.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(properties.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
        assertThat(properties.getProperty("management.endpoint.health.group.degraded.include"))
                .isEqualTo("degradedState,businessCache,messaging,redis");
    }

    @Test
    void degradedStatusMustRemainHttpReachable() {
        assertThat(properties.getProperty("management.endpoint.health.status.order"))
                .isEqualTo("DOWN,OUT_OF_SERVICE,DEGRADED,UNKNOWN,UP");
        assertThat(properties.getProperty(
                "management.endpoint.health.status.http-mapping.degraded"))
                .isEqualTo(200);
    }

    private static PropertySource<?> loadProperties() {
        try {
            return new YamlPropertySourceLoader()
                    .load("leetmodel-observability", new ClassPathResource(
                            "leetmodel-observability.yml"))
                    .get(0);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot load observability contract", exception);
        }
    }
}
