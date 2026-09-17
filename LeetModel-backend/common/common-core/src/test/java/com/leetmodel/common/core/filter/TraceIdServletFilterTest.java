package com.leetmodel.common.core.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdServletFilterTest {

    @AfterEach
    void clearMdc() {
        CorrelationContext.clear();
    }

    @Test
    void invalidInternalHeadersMustNotEnterMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/test");
        request.addHeader(CorrelationContext.TRACE_ID_HEADER, "forged trace id");
        request.addHeader(CorrelationContext.OPERATION_ID_HEADER, "forged\r\noperation");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<CorrelationSnapshot> observed = new AtomicReference<>();

        new TraceIdServletFilter().doFilter(request, response,
                (servletRequest, servletResponse) -> observed.set(CorrelationContext.capture()));

        assertThat(observed.get().traceId()).hasSize(32).isNotEqualTo("forged trace id");
        assertThat(observed.get().operationId()).isNull();
        assertThat(response.getHeader(CorrelationContext.TRACE_ID_HEADER))
                .isEqualTo(observed.get().traceId());
        assertThat(CorrelationContext.capture()).isEqualTo(CorrelationSnapshot.EMPTY);
    }

    @Test
    void trustedInternalHeadersMustExistOnlyInsideRequestScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/test");
        request.addHeader(CorrelationContext.TRACE_ID_HEADER, "trace-internal-1");
        request.addHeader(CorrelationContext.OPERATION_ID_HEADER, "operation-internal-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<CorrelationSnapshot> observed = new AtomicReference<>();

        new TraceIdServletFilter().doFilter(request, response,
                (servletRequest, servletResponse) -> observed.set(CorrelationContext.capture()));

        assertThat(observed.get().traceId()).isEqualTo("trace-internal-1");
        assertThat(observed.get().operationId()).isEqualTo("operation-internal-1");
        assertThat(CorrelationContext.capture()).isEqualTo(CorrelationSnapshot.EMPTY);
    }

    @Test
    void shouldEmitOneTemplatedAccessEventWithoutRawUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/problems/9137");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            new TraceIdServletFilter().doFilter(request, response, (servletRequest, servletResponse) -> {
                servletRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                        "/api/problems/{id}");
                ((MockHttpServletResponse) servletResponse).setStatus(200);
            });
        } finally {
            logger().detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(keyValues(event)).containsEntry("eventCode", LogEventCodes.HTTP_REQUEST_COMPLETED)
                .containsEntry("routeTemplate", "/api/problems/{id}")
                .containsEntry("httpMethod", "GET")
                .containsEntry("statusCode", 200);
        assertThat(event.getFormattedMessage()).doesNotContain("9137");
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        return appender;
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(TraceIdServletFilter.class);
    }

    private java.util.Map<String, Object> keyValues(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
