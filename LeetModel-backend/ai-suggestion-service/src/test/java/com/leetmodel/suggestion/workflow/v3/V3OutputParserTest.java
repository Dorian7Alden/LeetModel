package com.leetmodel.suggestion.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class V3OutputParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    record SampleSuggestion(
            String suggestionId,
            String priority,
            String actionPlanMarkdown,
            List<String> acceptanceCriteria
    ) {}

    @Test
    void shouldParsePureJsonObject() throws Exception {
        String raw = """
                {
                  "suggestionId": "S-1",
                  "priority": "P1",
                  "actionPlanMarkdown": "建议修复容量约束",
                  "acceptanceCriteria": ["误差小于 1e-4"]
                }
                """;

        SampleSuggestion result = V3OutputParser.parse(objectMapper, raw, SampleSuggestion.class);

        assertThat(result).isNotNull();
        assertThat(result.suggestionId()).isEqualTo("S-1");
        assertThat(result.priority()).isEqualTo("P1");
        assertThat(result.acceptanceCriteria()).containsExactly("误差小于 1e-4");
    }

    @Test
    void shouldExtractJsonFromMarkdownCodeFence() throws Exception {
        String raw = """
                好的，这是为您生成的优化建议：
                ```json
                {
                  "suggestionId": "S-2",
                  "priority": "P0",
                  "actionPlanMarkdown": "补全第一问漏答的灵敏度分析",
                  "acceptanceCriteria": ["给出扰动结果图"]
                }
                ```
                希望对您有帮助！
                """;

        SampleSuggestion result = V3OutputParser.parse(objectMapper, raw, SampleSuggestion.class);

        assertThat(result).isNotNull();
        assertThat(result.suggestionId()).isEqualTo("S-2");
        assertThat(result.priority()).isEqualTo("P0");
    }

    @Test
    void shouldHandleLatexBackslashesWithoutCrashing() throws Exception {
        // 大模型在 JSON 字符串中经常输出单反斜杠 \alpha, \frac 等非法转义
        String raw = "{\"suggestionId\":\"S-3\",\"priority\":\"P2\",\"actionPlanMarkdown\":\"改写目标函数为 \\alpha \\frac{a}{b}\",\"acceptanceCriteria\":[\"收敛\"]} ";

        SampleSuggestion result = V3OutputParser.parse(objectMapper, raw, SampleSuggestion.class);

        assertThat(result).isNotNull();
        assertThat(result.suggestionId()).isEqualTo("S-3");
        assertThat(result.actionPlanMarkdown()).contains("alpha");
    }

    @Test
    void shouldCleanBomAndControlCharacters() throws Exception {
        String raw = "\uFEFF\u0001{\n  \"suggestionId\": \"S-4\",\n  \"priority\": \"P3\",\n  \"actionPlanMarkdown\": \"排版美化\",\n  \"acceptanceCriteria\": [\"规范\"]\n}";

        SampleSuggestion result = V3OutputParser.parse(objectMapper, raw, SampleSuggestion.class);

        assertThat(result).isNotNull();
        assertThat(result.suggestionId()).isEqualTo("S-4");
    }

    @Test
    void shouldIgnoreUnknownProperties() throws Exception {
        String raw = """
                {
                  "suggestionId": "S-5",
                  "priority": "P1",
                  "actionPlanMarkdown": "正文",
                  "acceptanceCriteria": ["标准"],
                  "unknownField": "未来扩展属性"
                }
                """;

        SampleSuggestion result = V3OutputParser.parse(objectMapper, raw, SampleSuggestion.class);

        assertThat(result).isNotNull();
        assertThat(result.suggestionId()).isEqualTo("S-5");
    }
}
