package com.leetmodel.gateway.filter;

import com.leetmodel.common.core.management.ManagementAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementEndpointWebFilterTest {

    @Test
    void remoteMetricsRequestWithoutTokenMustBeHidden() {
        MockServerWebExchange exchange = exchange("/actuator/prometheus", null);
        AtomicBoolean continued = new AtomicBoolean();

        new ManagementEndpointWebFilter("trusted-token")
                .filter(exchange, current -> {
                    continued.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(continued).isFalse();
    }

    @Test
    void trustedMetricsRequestMustContinue() {
        MockServerWebExchange exchange = exchange("/actuator/prometheus", "trusted-token");
        AtomicBoolean continued = new AtomicBoolean();

        new ManagementEndpointWebFilter("trusted-token")
                .filter(exchange, current -> {
                    continued.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(continued).isTrue();
    }

    private MockServerWebExchange exchange(String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress("203.0.113.8", 43123));
        if (token != null) request.header(ManagementAccessPolicy.TOKEN_HEADER, token);
        return MockServerWebExchange.from(request);
    }
}
