package com.leetmodel.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        CorrelationContext.clear();
    }

    @Test
    void publicBoundaryMustDiscardSpoofedCorrelationHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/public/problems")
                        .header(CorrelationContext.TRACE_ID_HEADER, "client-trace")
                        .header(CorrelationContext.OPERATION_ID_HEADER, "client-operation")
                        .header("X-Sw-Trace-Id", "client-sw-trace")
                        .header("X-Sw-Span-Id", "client-sw-span")
                        .header("X-Event-Id", "client-event")
                        .header("X-Domain-Task-Id", "client-task")
                        .header("X-Attempt-No", "7")
                        .header("X-Ai-Call-Id", "client-ai-call")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        new TraceIdFilter().filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        }).block();

        String trustedTraceId = forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationContext.TRACE_ID_HEADER);
        assertThat(trustedTraceId).hasSize(32).isNotEqualTo("client-trace");
        assertThat(forwarded.get().getRequest().getHeaders())
                .doesNotContainKeys(
                        CorrelationContext.OPERATION_ID_HEADER,
                        "X-Sw-Trace-Id",
                        "X-Sw-Span-Id",
                        "X-Event-Id",
                        "X-Domain-Task-Id",
                        "X-Attempt-No",
                        "X-Ai-Call-Id"
                );
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationContext.TRACE_ID_HEADER))
                .isEqualTo(trustedTraceId);
        assertThat(CorrelationContext.capture()).isEqualTo(CorrelationSnapshot.EMPTY);
    }

    @Test
    void shouldEmitOneStableAccessEventWithoutRawPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/problems/private-9137"));
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            new TraceIdFilter().filter(exchange, current -> Mono.empty()).block();
        } finally {
            logger().detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(keyValues(event)).containsEntry("eventCode", LogEventCodes.HTTP_REQUEST_COMPLETED)
                .containsEntry("routeTemplate", "UNMATCHED")
                .containsEntry("httpMethod", "GET")
                .containsEntry("statusCode", 200);
        assertThat(event.getFormattedMessage()).doesNotContain("private-9137");
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        return appender;
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
    }

    private java.util.Map<String, Object> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
