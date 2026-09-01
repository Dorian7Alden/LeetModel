package com.leetmodel.gateway.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WebFluxRouteMetricContractTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=webflux-route-contract-test",
                "spring.main.web-application-type=reactive",
                "spring.config.import=classpath:leetmodel-observability.yml",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
@AutoConfigureObservability
class WebFluxRouteMetricContractTest {

    @LocalServerPort
    private int port;

    @jakarta.annotation.Resource
    private MeterRegistry registry;

    @Test
    void usesRouteTemplateInsteadOfConcreteIdentifier() {
        WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build()
                .get().uri("/reactive-metric-items/987654321")
                .exchange()
                .expectStatus().isOk();

        assertThat(registry.find("http.server.requests")
                .tag("uri", "/reactive-metric-items/{itemId}").timer()).isNotNull();
        assertThat(registry.find("http.server.requests")
                .tag("uri", "/reactive-metric-items/987654321").timer()).isNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TestController.class)
    static class TestApplication {
    }

    @RestController
    static class TestController {
        @GetMapping("/reactive-metric-items/{itemId}")
        String item(@PathVariable long itemId) {
            return Long.toString(itemId);
        }
    }
}
