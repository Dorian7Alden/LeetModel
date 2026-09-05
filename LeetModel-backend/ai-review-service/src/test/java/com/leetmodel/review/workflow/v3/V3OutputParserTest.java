package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
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
}
