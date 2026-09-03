package com.leetmodel.common.core.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkyWalkingExecutionSpanTest {

    @AfterEach
    void clear() {
        CorrelationContext.clear();
    }

    @Test
    void shouldRestoreBusinessCorrelationAndRemainNoopWithoutAgent() {
        CorrelationSnapshot previous = CorrelationSnapshot.EMPTY.withTraceId("outer-trace");
        CorrelationContext.replace(previous);
        CorrelationSnapshot attempt = CorrelationSnapshot.EMPTY
                .withTraceId("business-trace")
                .withDomainTask("task-41", 3)
                .withAiCallId("call-51");

        try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                ExecutionSpanOperation.AI_PROVIDER, attempt)) {
            span.attemptKind(true).aiCallType("CHAT").aiPriority("P0")
                    .outcome("upstream_result_unknown").error("result_unknown");
            assertThat(CorrelationContext.capture())
                    .extracting(CorrelationSnapshot::traceId,
                            CorrelationSnapshot::domainTaskId,
                            CorrelationSnapshot::attemptNo,
                            CorrelationSnapshot::aiCallId)
                    .containsExactly("business-trace", "task-41", 3, "call-51");
        }

        assertThat(CorrelationContext.capture()).isEqualTo(previous);
    }

    @Test
    void shouldOnlyAcceptBoundedCategoryValues() {
        assertThat(SkyWalkingExecutionSpan.isLowCardinalityValue("upstream_result_unknown")).isTrue();
        assertThat(SkyWalkingExecutionSpan.isLowCardinalityValue("P4")).isTrue();
        assertThat(SkyWalkingExecutionSpan.isLowCardinalityValue(
                "00000000-0000-4000-8000-000000000001")).isFalse();
        assertThat(SkyWalkingExecutionSpan.isLowCardinalityValue("raw error with payload")).isFalse();
        assertThat(SkyWalkingExecutionSpan.isLowCardinalityValue("x".repeat(65))).isFalse();
    }

    @Test
    void operationsAreClosedFixedCatalog() {
        assertThat(ExecutionSpanOperation.values())
                .extracting(ExecutionSpanOperation::operationName)
                .containsExactly(
                        "Messaging/OutboxPublishAttempt",
                        "Messaging/InboxConsumeAttempt",
                        "Worker/ReviewAttempt",
                        "Worker/SuggestionAttempt",
                        "Worker/EvaluationAttempt",
                        "Worker/RankingRebuildAttempt",
                        "AI/ProviderAttempt",
                        "AI/RecoveryAttempt"
                )
                .allMatch(name -> !name.matches(".*[{}?=].*"));
    }
}
