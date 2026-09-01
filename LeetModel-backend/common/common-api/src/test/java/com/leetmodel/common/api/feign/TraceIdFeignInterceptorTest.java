package com.leetmodel.common.api.feign;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFeignInterceptorTest {

    private final TraceIdFeignInterceptor interceptor = new TraceIdFeignInterceptor();

    @AfterEach
    void clearMdc() {
        CorrelationContext.clear();
    }

    @Test
    void trustedContextMustReplaceAnyPreconfiguredHeaders() {
        RequestTemplate template = new RequestTemplate();
        template.header(CorrelationContext.TRACE_ID_HEADER, "forged-trace");
        template.header(CorrelationContext.OPERATION_ID_HEADER, "forged-operation");
        CorrelationSnapshot trusted = CorrelationSnapshot.EMPTY
                .withTraceId("trace-trusted")
                .withOperationId("operation-trusted");

        try (CorrelationContext.Scope ignored = CorrelationContext.open(trusted)) {
            interceptor.apply(template);
        }

        assertThat(template.headers().get(CorrelationContext.TRACE_ID_HEADER))
                .containsExactly("trace-trusted");
        assertThat(template.headers().get(CorrelationContext.OPERATION_ID_HEADER))
                .containsExactly("operation-trusted");
    }

    @Test
    void missingTrustedContextMustRemovePreconfiguredHeaders() {
        RequestTemplate template = new RequestTemplate();
        template.header(CorrelationContext.TRACE_ID_HEADER, "forged-trace");
        template.header(CorrelationContext.OPERATION_ID_HEADER, "forged-operation");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys(
                CorrelationContext.TRACE_ID_HEADER,
                CorrelationContext.OPERATION_ID_HEADER
        );
    }
}
