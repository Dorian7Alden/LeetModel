package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 仅由真实 SkyWalking 协议门禁启用；向 OAP 发出可区分的独立 attempt Trace。
 */
@EnabledIfEnvironmentVariable(named = "RUN_SKYWALKING_ASYNC_INTEGRATION", matches = "true")
class SkyWalkingExecutionSpanAgentIntegrationTest {

    @Test
    void emitsIndependentBoundedAttemptSpans() {
        emit(ExecutionSpanOperation.REVIEW_WORKER, "async-review-normal", 1,
                span -> span.attemptKind(false).outcome("succeeded"));
        emit(ExecutionSpanOperation.REVIEW_WORKER, "async-review-takeover", 2,
                span -> span.attemptKind(true).outcome("failed").error("domain_failure"));
        emit(ExecutionSpanOperation.AI_PROVIDER, "async-ai-success", 1,
                span -> span.attemptKind(false).aiCallType("chat").aiPriority("p0")
                        .outcome("succeeded"));
        emit(ExecutionSpanOperation.AI_PROVIDER, "async-ai-unknown", 2,
                span -> span.attemptKind(true).aiCallType("embedding").aiPriority("p4")
                        .outcome("upstream_result_unknown").error("result_unknown"));
        emit(ExecutionSpanOperation.AI_RECOVERY, "async-ai-recovery", 2,
                span -> span.aiCallType("chat").aiPriority("p3")
                        .outcome("requeued_before_dispatch"));
    }

    private void emit(
            ExecutionSpanOperation operation,
            String businessTraceId,
            int attemptNo,
            java.util.function.Consumer<SkyWalkingExecutionSpan> action
    ) {
        CorrelationSnapshot correlation = CorrelationSnapshot.EMPTY
                .withTraceId(businessTraceId)
                .withDomainTask("contract-task", attemptNo)
                .withAiCallId("contract-call");
        try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(operation, correlation)) {
            action.accept(span);
        }
    }
}
