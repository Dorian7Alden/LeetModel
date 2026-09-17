package com.leetmodel.gateway.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelBlockRequestHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SentinelBlockRequestHandler handler = new SentinelBlockRequestHandler(objectMapper);

    @Test
    @DisplayName("限流触发时应返回 HTTP 429 且载荷符合 Result 规范，继承现有 TraceId")
    void shouldReturn429WithResultPayloadAndInheritTraceId() {
        String givenTraceId = "test-trace-id-1234567890abcdef12";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/public/problems")
                        .header(CorrelationContext.TRACE_ID_HEADER, givenTraceId)
        );
        Route mockRoute = Route.async()
                .id("problem-public")
                .uri(URI.create("lb://problem-service"))
                .order(0)
                .predicate(swe -> true)
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, mockRoute);

        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            ServerResponse response = handler.handleRequest(
                    exchange,
                    new FlowException("problem-public")
            ).block();

            assertThat(response).isNotNull();
            assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(response.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.headers().getFirst(CorrelationContext.TRACE_ID_HEADER))
                    .isEqualTo(givenTraceId);

            // 验证响应体 JSON 内容
            String bodyJson = extractResponseBody(response);
            try {
                JsonNode jsonNode = objectMapper.readTree(bodyJson);
                assertThat(jsonNode.get("code").asInt())
                        .isEqualTo(ErrorCodeEnum.RATE_LIMITED.getCode());
                assertThat(jsonNode.get("message").asText())
                        .isEqualTo(ErrorCodeEnum.RATE_LIMITED.getMessage());
                assertThat(jsonNode.get("data").isNull()).isTrue();
                assertThat(jsonNode.has("timestamp")).isTrue();
            } catch (Exception e) {
                throw new AssertionError("解析响应体 JSON 失败", e);
            }
        } finally {
            logger().detachAppender(appender);
        }

        // 验证结构化日志输出
        assertThat(appender.list).isNotEmpty();
        ILoggingEvent logEvent = appender.list.get(0);
        Map<String, Object> keyValues = keyValues(logEvent);
        assertThat(keyValues)
                .containsEntry("eventCode", LogEventCodes.CAPACITY_PROTECTION_ACTIVATED)
                .containsEntry("traceId", givenTraceId)
                .containsEntry("routeTemplate", "route:problem-public")
                .containsEntry("statusCode", 429)
                .containsEntry("errorCode", ErrorCodeEnum.RATE_LIMITED.getCode());
    }

    @Test
    @DisplayName("当请求头中无 TraceId 时应自动生成新的 TraceId 并注入响应头")
    void shouldGenerateTraceIdWhenMissingInRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
        );

        ServerResponse response = handler.handleRequest(
                exchange,
                new FlowException("user-auth")
        ).block();

        assertThat(response).isNotNull();
        String traceId = response.headers().getFirst(CorrelationContext.TRACE_ID_HEADER);
        assertThat(traceId).isNotNull().hasSize(32);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private String extractResponseBody(ServerResponse response) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/mock-extract")
        );
        ServerResponse.Context context = new ServerResponse.Context() {
            @Override
            public java.util.List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return HandlerStrategies.withDefaults().messageWriters();
            }

            @Override
            public java.util.List<org.springframework.web.reactive.result.view.ViewResolver> viewResolvers() {
                return java.util.Collections.emptyList();
            }
        };
        response.writeTo(exchange, context).block();
        return exchange.getResponse().getBodyAsString().block();
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        return appender;
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(SentinelBlockRequestHandler.class);
    }

    private Map<String, Object> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
