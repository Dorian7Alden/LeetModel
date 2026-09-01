package com.leetmodel.common.core.filter;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

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
}
