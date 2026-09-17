package com.leetmodel.gateway;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayApplicationTest {

    @BeforeAll
    static void configurePropagation() {
        GatewayApplication.configureContextPropagation();
    }

    @AfterAll
    static void resetPropagation() {
        Hooks.disableAutomaticContextPropagation();
        ContextRegistry.getInstance().removeThreadLocalAccessor(CorrelationContext.REACTOR_CONTEXT_KEY);
        CorrelationContext.clear();
    }

    @Test
    void shouldRestoreCorrelationMdcAcrossReactorThreadSwitchWithoutLeaking() {
        CorrelationSnapshot snapshot = CorrelationSnapshot.EMPTY
                .withTraceId("trace-reactor")
                .withOperationId("operation-reactor");

        CorrelationSnapshot observed = Mono.fromCallable(CorrelationContext::capture)
                .subscribeOn(Schedulers.boundedElastic())
                .contextWrite(context -> context.put(CorrelationContext.REACTOR_CONTEXT_KEY, snapshot))
                .block();

        assertThat(observed).isEqualTo(snapshot);
        assertThat(CorrelationContext.capture()).isEqualTo(CorrelationSnapshot.EMPTY);
    }
}
