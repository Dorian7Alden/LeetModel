package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class V3OutputParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExtractJsonFromMarkdownCodeFence() {
        String raw = """
                好的，这是针对该小题的评审结果，请查收：
                ```json
                {
                  "score": 22.5,
                  "maxScore": 25.0
                }
                ```
                希望能对队伍有所帮助！
                """;

        String json = V3OutputParser.extractJson(raw);
        assertThat(json).startsWith("{").endsWith("}");
        assertThat(json).contains("\"score\": 22.5");
        assertThat(json).doesNotContain("```");
        assertThat(json).doesNotContain("好的，这是针对");
    }

    @Test
    void shouldLenientlyParseDtoWithUnknownPropertiesAndBOM() throws Exception {
        String rawWithBom = "\uFEFF```json\n" +
                "{\n" +
                "  \"score\": 24.0,\n" +
                "  \"maxScore\": 25.0,\n" +
                "  \"extraUnknownField\": \"should be ignored\",\n" +
                "  \"aspects\": []\n" +
                "}\n" +
                "```";

        Phase1StructuralReviewResultDTO dto = V3OutputParser.parse(
                objectMapper, rawWithBom, Phase1StructuralReviewResultDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.getScore()).isEqualTo(BigDecimal.valueOf(24.0));
        assertThat(dto.getMaxScore()).isEqualTo(BigDecimal.valueOf(25.0));
    }

    @Test
    void shouldNormalizeStringObservationsIntoStructuredItems() throws Exception {
        String raw = """
                {
                  "score": 13.5,
                  "observations": [
                    "论文构建了完整的建模与检验链条。"
                  ],
                  "findings": []
                }
                """;

        SubTaskEvaluationResultDTO dto = V3OutputParser.parse(
                objectMapper,
                raw,
                SubTaskEvaluationResultDTO.class
        );

        assertThat(dto.getObservations()).singleElement().satisfies(observation -> {
            assertThat(observation.getObservationId()).isEqualTo("OBS_MODEL_1");
            assertThat(observation.getObservationType()).isEqualTo("MODEL_SUMMARY");
            assertThat(observation.getSummary()).isEqualTo("论文构建了完整的建模与检验链条。");
        });
    }

    @Test
    void shouldRepairInvalidLatexEscapesWithoutChangingValidJsonEscapes() throws Exception {
        String raw = """
                {
                  "score": 18.0,
                  "maxScore": 25.0,
                  "aspects": [
                    {
                      "aspectCode": "FORMULA",
                      "aspectName": "公式",
                      "maxScore": 5.0,
                      "score": 4.0,
                      "reason": "似然函数为 $\\lambda=\\frac{1}{2}$，并保留换行\\n继续说明。",
                      "findingIds": []
                    }
                  ]
                }
                """;

        Phase1StructuralReviewResultDTO dto = V3OutputParser.parse(
                objectMapper,
                raw,
                Phase1StructuralReviewResultDTO.class
        );

        assertThat(dto.getAspects().get(0).getReason())
                .isEqualTo("似然函数为 $\\lambda=\\frac{1}{2}$，并保留换行\n继续说明。");
    }

    @Test
    void shouldKeepAlreadyEscapedLatexCommandsInSubTaskOutput() throws Exception {
        String raw = """
                {
                  "taskId": "TASK_SENSITIVITY_EVAL",
                  "score": 12.0,
                  "maxScore": 15.0,
                  "evaluationSummary": "扰动量采用 $\\\\Delta p$，结果项包含 $x \\\\cdot y$。",
                  "observations": [],
                  "findings": []
                }
                """;

        SubTaskEvaluationResultDTO dto = V3OutputParser.parse(
                objectMapper,
                raw,
                SubTaskEvaluationResultDTO.class
        );

        assertThat(dto.getEvaluationSummary())
                .isEqualTo("扰动量采用 $\\Delta p$，结果项包含 $x \\cdot y$。");
    }
}
