package com.leetmodel.evaluation.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationSamplePayloadServiceTest {

    private final EvaluationSamplePayloadService service =
            new EvaluationSamplePayloadService(JsonMapper.builder().build());

    @Test
    void reviewPayloadKeepsOnlySubmissionReference() {
        var result = service.validate("REVIEW", new EvaluationSamplePayloadDTO(
                "SUBMISSION_REFERENCE", "REVIEW_SUBMISSION_V1", "{\"submissionId\":31}"));

        assertThat(result.submissionId()).isEqualTo(31L);
        assertThat(result.payloadJson()).isEqualTo("{\"submissionId\":31}");
    }

    @Test
    void assistantPayloadSupportsQuestionTagsAndOptionalExpectedPoints() {
        var result = service.validate("ASSISTANT", new EvaluationSamplePayloadDTO(
                "ASSISTANT_QUESTION", "ASSISTANT_QUESTION_V1",
                "{\"question\":\"如何提交论文？\",\"tags\":[\"提交\"],"
                        + "\"expectedPoints\":[\"只接受 PDF\"]}"));

        assertThat(result.submissionId()).isNull();
        assertThat(result.payloadJson()).contains("expectedPoints");
    }

    @Test
    void unknownFieldsCannotSmuggleLocalPathsOrPdfContent() {
        assertThatThrownBy(() -> service.validate("REVIEW", new EvaluationSamplePayloadDTO(
                "SUBMISSION_REFERENCE", "REVIEW_SUBMISSION_V1",
                "{\"submissionId\":31,\"pdfPath\":\"/home/user/paper.pdf\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知字段");
    }

    @Test
    void suggestionCannotEnterDatasetBeforeOwnerContractExists() {
        assertThatThrownBy(() -> service.validate("SUGGESTION", new EvaluationSamplePayloadDTO(
                "SUBMISSION_REFERENCE", "REVIEW_SUBMISSION_V1", "{\"submissionId\":31}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的评价功能");
    }
}
