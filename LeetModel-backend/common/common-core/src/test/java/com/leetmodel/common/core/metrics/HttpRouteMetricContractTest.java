package com.leetmodel.common.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HttpRouteMetricContractTest.TestApplication.class,
        properties = {
                "spring.application.name=http-route-contract-test",
                "spring.config.import=classpath:leetmodel-observability.yml"
        })
@AutoConfigureMockMvc
@AutoConfigureObservability
class HttpRouteMetricContractTest {

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @jakarta.annotation.Resource
    private MeterRegistry registry;

    @Test
    void usesRouteTemplateInsteadOfConcreteIdentifier() throws Exception {
        mockMvc.perform(get("/metric-items/987654321"))
                .andExpect(status().isOk());

        var timer = registry.find("http.server.requests")
                .tag("uri", "/metric-items/{itemId}")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(registry.find("http.server.requests")
                .tag("uri", "/metric-items/987654321").timer()).isNull();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    @Import(TestController.class)
    static class TestApplication {
    }

    @RestController
    static class TestController {
        @GetMapping("/metric-items/{itemId}")
        String item(@PathVariable long itemId) {
            return Long.toString(itemId);
        }
    }
}
