package com.leetmodel.aigateway.scheduling;

import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiOperationCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AiPriorityPolicyTest {

    private final AiPriorityPolicy policy = new AiPriorityPolicy();

    @Test
    void fixesAllApprovedPriorityClasses() {
        assertPriority("ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.CHAT_REPLY, AiCallPriority.P0);
        assertPriority("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.RETRIEVE_CONTEXT, AiCallPriority.P0);
        assertPriority("ai-review-service", AiFeatureCode.PAPER_REVIEW,
                AiOperationCode.FORMAL_REVIEW, AiCallPriority.P1);
        assertPriority("ai-suggestion-service", AiFeatureCode.PAPER_SUGGESTION,
                AiOperationCode.GENERATE_SUGGESTION, AiCallPriority.P1);
        assertPriority("admin-service", AiFeatureCode.ADMIN_TEST,
                AiOperationCode.ADMIN_MODEL_TEST, AiCallPriority.P2);
        assertPriority("ai-review-service", AiFeatureCode.PAPER_REVIEW,
                AiOperationCode.EXPERIMENT_REVIEW, AiCallPriority.P3);
        assertPriority("ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.EXPERIMENT_ASSISTANT, AiCallPriority.P3);
        assertPriority("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.INDEX_DOCUMENTS, AiCallPriority.P4);
    }

    @Test
    void overridesAttemptedPromotionAndDefaultsUnknownSourceToP3() {
        AiPriorityPolicy.PriorityDecision promoted = policy.resolve(context("ai-review-service",
                AiFeatureCode.PAPER_REVIEW, AiOperationCode.EXPERIMENT_REVIEW, AiCallPriority.P0));
        assertThat(promoted.effectivePriority()).isEqualTo(AiCallPriority.P3);
        assertThat(promoted.declaredAccepted()).isFalse();
        assertThat(promoted.reason()).isEqualTo("DECLARATION_OVERRIDDEN");

        AiPriorityPolicy.PriorityDecision unknown = policy.resolve(context("unknown-service",
                AiFeatureCode.LEGACY, AiOperationCode.LEGACY_CHAT, AiCallPriority.P0));
        assertThat(unknown.effectivePriority()).isEqualTo(AiCallPriority.P3);
        assertThat(unknown.declaredAccepted()).isFalse();
        assertThat(unknown.reason()).isEqualTo("UNKNOWN_SOURCE");
    }

    private void assertPriority(String caller, AiFeatureCode feature, AiOperationCode operation,
                                AiCallPriority expected) {
        AiPriorityPolicy.PriorityDecision result = policy.resolve(context(caller, feature, operation, expected));
        assertThat(result.effectivePriority()).isEqualTo(expected);
        assertThat(result.declaredAccepted()).isTrue();
        assertThat(result.reason()).isEqualTo("MATCHED");
    }

    private AiCallContext context(String caller, AiFeatureCode feature, AiOperationCode operation,
                                  AiCallPriority priority) {
        return new AiCallContext(caller, feature, operation, "task", null, null,
                "model-version", null, priority, "idempotency", Instant.now().plusSeconds(60));
    }
}
