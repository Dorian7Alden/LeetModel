package com.leetmodel.common.api.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiExperimentContractTest {

    @Test
    void expressesReviewAndAssistantWithoutBusinessSideEffects() {
        var review = request("run-review-1", "REVIEW", "SUBMISSION_REFERENCE",
                "REVIEW_SUBMISSION_V1", "{\"submissionId\":\"31\"}",
                "BASIC_REVIEW_V1", "MODEL_CFG_REVIEW_MULTIMODAL_0001", null);
        var assistant = request("run-assistant-1", "ASSISTANT", "QUESTION",
                "ASSISTANT_QUESTION_V1", "{\"question\":\"如何选择题目？\"}",
                "ASSISTANT_NO_RAG_V1", "MODEL_CFG_ASSISTANT_TEXT_0001", null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(review)).isEmpty();
            assertThat(validator.validate(assistant)).isEmpty();
        }
        assertThat(review.getSample().getPayloadJson()).doesNotContain("formalTaskId");
        assertThat(assistant.getSample().getPayloadJson()).doesNotContain("conversationId");
    }

    @Test
    void rejectsUntrustedPriorityAndMissingModelConfig() {
        var request = request("run-1", "REVIEW", "SUBMISSION_REFERENCE",
                "REVIEW_SUBMISSION_V1", "{\"submissionId\":\"31\"}",
                "BASIC_REVIEW_V1", "", null);
        request.setPriority("P9");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("priority", "modelExecutionConfigVersion");
        }
    }

    private AiExperimentRequestDTO request(String runId, String feature, String sampleType,
                                           String sampleSchema, String payload, String workflow,
                                           String modelConfig, String ragIndex) {
        return new AiExperimentRequestDTO(runId, feature,
                new AiExperimentSampleDTO(sampleType, sampleSchema, payload), workflow,
                modelConfig, ragIndex, "P3");
    }
}
