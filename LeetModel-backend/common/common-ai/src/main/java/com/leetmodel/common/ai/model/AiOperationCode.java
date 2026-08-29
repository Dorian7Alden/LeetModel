package com.leetmodel.common.ai.model;

/** AI 能力域内的原子操作。 */
public enum AiOperationCode {
    CHAT_REPLY(AiFeatureCode.AI_ASSISTANT),
    EXPERIMENT_ASSISTANT(AiFeatureCode.AI_ASSISTANT),
    FORMAL_REVIEW(AiFeatureCode.PAPER_REVIEW),
    EXPERIMENT_REVIEW(AiFeatureCode.PAPER_REVIEW),
    GENERATE_SUGGESTION(AiFeatureCode.PAPER_SUGGESTION),
    ADMIN_MODEL_TEST(AiFeatureCode.ADMIN_TEST),
    INDEX_DOCUMENTS(AiFeatureCode.RAG),
    RETRIEVE_CONTEXT(AiFeatureCode.RAG),
    LEGACY_CHAT(AiFeatureCode.LEGACY);

    private final AiFeatureCode featureCode;

    AiOperationCode(AiFeatureCode featureCode) {
        this.featureCode = featureCode;
    }

    public boolean belongsTo(AiFeatureCode featureCode) {
        return this.featureCode == featureCode;
    }
}
