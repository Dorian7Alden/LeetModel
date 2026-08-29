package com.leetmodel.aigateway.scheduling;

import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiOperationCode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 根据可信调用方和稳定 operation 固定有效优先级，拒绝调用方自行提权。 */
@Component
public class AiPriorityPolicy {

    private static final AiCallPriority SAFE_DEFAULT = AiCallPriority.P3;
    private static final Map<Key, AiCallPriority> RULES = Map.of(
            new Key("ai-assistant-service", AiOperationCode.CHAT_REPLY), AiCallPriority.P0,
            new Key("ai-assistant-service", AiOperationCode.RETRIEVE_CONTEXT), AiCallPriority.P0,
            new Key("ai-review-service", AiOperationCode.FORMAL_REVIEW), AiCallPriority.P1,
            new Key("ai-suggestion-service", AiOperationCode.GENERATE_SUGGESTION), AiCallPriority.P1,
            new Key("admin-service", AiOperationCode.ADMIN_MODEL_TEST), AiCallPriority.P2,
            new Key("ai-review-service", AiOperationCode.EXPERIMENT_REVIEW), AiCallPriority.P3,
            new Key("ai-assistant-service", AiOperationCode.EXPERIMENT_ASSISTANT), AiCallPriority.P3,
            new Key("ai-assistant-service", AiOperationCode.INDEX_DOCUMENTS), AiCallPriority.P4);

    public PriorityDecision resolve(AiCallContext context) {
        if (context == null || context.callerService() == null || context.operationCode() == null) {
            return new PriorityDecision(SAFE_DEFAULT, false, "MISSING_CONTEXT");
        }
        AiCallPriority effective = RULES.get(new Key(context.callerService(), context.operationCode()));
        if (effective == null) {
            return new PriorityDecision(SAFE_DEFAULT, context.priority() == SAFE_DEFAULT, "UNKNOWN_SOURCE");
        }
        boolean declaredAccepted = effective == context.priority();
        return new PriorityDecision(effective, declaredAccepted,
                declaredAccepted ? "MATCHED" : "DECLARATION_OVERRIDDEN");
    }

    public record PriorityDecision(AiCallPriority effectivePriority, boolean declaredAccepted, String reason) {}

    private record Key(String callerService, AiOperationCode operationCode) {}
}
