package com.leetmodel.common.api.feign;

import feign.Client;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SkyWalkingFeignCapabilityTest {

    @Test
    void shouldBeTransparentWithoutAgentAndAvoidDynamicUrlInOperationName() throws Exception {
        AtomicReference<Request> observed = new AtomicReference<>();
        Client delegate = (request, options) -> {
            observed.set(request);
            return Response.builder()
                    .status(200)
                    .reason("OK")
                    .request(request)
                    .headers(Map.of())
                    .build();
        };
        Client client = new SkyWalkingFeignCapability().enrich(delegate);
        Request first = request("http://127.0.0.1:18081/internal/users/123?token=secret");
        Request second = request("http://127.0.0.1:18081/internal/users/456?token=other");

        assertThat(SkyWalkingFeignCapability.TracingClient.operationName(first))
                .isEqualTo("Feign/GET")
                .isEqualTo(SkyWalkingFeignCapability.TracingClient.operationName(second));
        assertThat(client.execute(first, new Request.Options()).status()).isEqualTo(200);
        assertThat(observed.get().headers()).doesNotContainKeys("sw8", "SW8");
    }

    private Request request(String url) {
        return Request.create(Request.HttpMethod.GET, url, Map.of(), new byte[0],
                StandardCharsets.UTF_8);
    }
}
