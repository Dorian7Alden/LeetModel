package com.leetmodel.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteConfigurationTest {

    @Test
    void mvpPublicRoutesMustAllBeRegistered() throws Exception {
        var properties = new YamlPropertySourceLoader()
                .load("gateway-dev", new ClassPathResource("application-dev.yml"))
                .get(0);
        Set<String> routes = new HashSet<>();

        for (int index = 0; ; index++) {
            Object routeId = properties.getProperty("spring.cloud.gateway.routes[" + index + "].id");
            if (routeId == null) {
                break;
            }
            routes.add(routeId.toString());
        }

        assertThat(routes).contains(
                "user-auth", "user-service", "team-service", "problem-service",
                "problem-public", "tag-service", "contest-service", "submission-service",
                "ai-review-service", "ranking-service", "ai-suggestion-service",
                "ai-assistant-service", "admin-service"
        );
    }
}
