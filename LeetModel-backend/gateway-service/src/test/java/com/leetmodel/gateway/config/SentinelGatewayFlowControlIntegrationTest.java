package com.leetmodel.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.gateway.handler.SentinelBlockRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelGatewayFlowControlIntegrationTest {

    private static final String TEST_ROUTE_ID = "test-problem-route";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SentinelGatewayFilter gatewayFilter;
    private SentinelGatewayBlockExceptionHandler blockExceptionHandler;

    @BeforeEach
    void setUp() {
        SentinelBlockRequestHandler blockRequestHandler = new SentinelBlockRequestHandler(objectMapper);
        GatewayCallbackManager.setBlockHandler(blockRequestHandler);

        List<ViewResolver> viewResolvers = Collections.emptyList();
        ServerCodecConfigurer serverCodecConfigurer = ServerCodecConfigurer.create();
        blockExceptionHandler = new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
        gatewayFilter = new SentinelGatewayFilter();

        // 配置单 QPS 阈值为 1 的网关测试规则
        Set<GatewayFlowRule> rules = new HashSet<>();
        GatewayFlowRule rule = new GatewayFlowRule(TEST_ROUTE_ID)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(1)
                .setIntervalSec(1);
        rules.add(rule);
        GatewayRuleManager.loadRules(rules);
    }

    @AfterEach
    void tearDown() {
        GatewayRuleManager.loadRules(new HashSet<>());
        GatewayCallbackManager.resetBlockHandler();
    }

    @Test
    @DisplayName("端到端流控：突发请求超出 QPS 阈值后触发统一 429 降级信封与 TraceId 关联")
    void shouldPassAllowedRequestsAndBlockSubsequentSpikeWithStandardPayload() throws Exception {
        Route route = Route.async()
                .id(TEST_ROUTE_ID)
                .uri(URI.create("lb://problem-service"))
                .order(0)
                .predicate(exchange -> true)
                .build();

        AtomicInteger downstreamInvocations = new AtomicInteger(0);
        GatewayFilterChain mockDownstreamChain = exchange -> Mono.fromRunnable(downstreamInvocations::incrementAndGet);

        // 1. 发起首个请求：在阈值内，应当正常放行至下游
        MockServerWebExchange exchange1 = createMockExchange(route, "trace-req-001");
        gatewayFilter.filter(exchange1, mockDownstreamChain).block();
        assertThat(downstreamInvocations.get()).isEqualTo(1);
        assertThat(exchange1.getResponse().getStatusCode()).isNull(); // 尚未写入错误状态

        // 2. 发起第二个请求：窗口内达到上限
        MockServerWebExchange exchange2 = createMockExchange(route, "trace-req-002");
        gatewayFilter.filter(exchange2, mockDownstreamChain).block();
        assertThat(downstreamInvocations.get()).isEqualTo(2);

        // 3. 发起第三个突发请求：超出滑动窗口 QPS 阈值，网关 Filter 抛出 BlockException
        MockServerWebExchange exchange3 = createMockExchange(route, "trace-req-003");
        Mono<Void> thirdExecution = gatewayFilter.filter(exchange3, mockDownstreamChain)
                .onErrorResume(throwable -> blockExceptionHandler.handle(exchange3, throwable));
        thirdExecution.block();

        // 下游不应接收到第三次请求，调用次数保持为 2
        assertThat(downstreamInvocations.get()).isEqualTo(2);

        // 验证返回状态码为 429
        assertThat(exchange3.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // 验证响应头携带 X-Trace-Id
        assertThat(exchange3.getResponse().getHeaders().getFirst(CorrelationContext.TRACE_ID_HEADER))
                .isEqualTo("trace-req-003");

        // 验证响应体为规范的 Result JSON
        String responseBody = exchange3.getResponse().getBodyAsString().block();
        assertThat(responseBody).isNotBlank();

        JsonNode root = objectMapper.readTree(responseBody);
        assertThat(root.get("code").asInt()).isEqualTo(ErrorCodeEnum.RATE_LIMITED.getCode());
        assertThat(root.get("message").asText()).isEqualTo(ErrorCodeEnum.RATE_LIMITED.getMessage());
        assertThat(root.get("data").isNull()).isTrue();
        assertThat(root.has("timestamp")).isTrue();
    }

    private MockServerWebExchange createMockExchange(Route route, String traceId) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/public/problems")
                .header(CorrelationContext.TRACE_ID_HEADER, traceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        exchange.getResponse().getHeaders().set(CorrelationContext.TRACE_ID_HEADER, traceId);
        return exchange;
    }
}
