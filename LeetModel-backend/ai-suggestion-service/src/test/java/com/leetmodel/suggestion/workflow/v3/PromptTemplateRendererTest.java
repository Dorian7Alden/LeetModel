package com.leetmodel.suggestion.workflow.v3;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    @Test
    void shouldRenderSafelyWithLaTeXAndDollarSigns() {
        String template = "题目：[[title]]\n公式：[[formula]]\n金额：[[price]]\n描述：[[desc]]";
        Map<String, String> variables = Map.of(
                "title", "多时段车辆路径规划",
                "formula", "\\min \\sum_{i=1}^{n} c_{ij} x_{ij} + \\frac{\\alpha}{\\beta}",
                "price", "$500 和 $1000",
                "desc", "正文中包含 {字面花括号} 与 $1 正则特殊字符"
        );

        String rendered = PromptTemplateRenderer.render(template, variables);

        assertThat(rendered).contains("多时段车辆路径规划");
        assertThat(rendered).contains("\\min \\sum_{i=1}^{n} c_{ij} x_{ij} + \\frac{\\alpha}{\\beta}");
        assertThat(rendered).contains("$500 和 $1000");
        assertThat(rendered).contains("{字面花括号}");
        assertThat(rendered).contains("$1");
        assertThat(rendered).doesNotContain("[[title]]");
    }

    @Test
    void shouldLoadSuggestionClasspathPromptsWithoutError() {
        String plannerPrompt = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-suggestion-planner.st");
        assertThat(plannerPrompt).contains("STRUCTURAL_SUGGESTION");
        assertThat(plannerPrompt).contains("SUB_PROBLEM_SUGGESTION");
        assertThat(plannerPrompt).contains("[[problemQuestionsList]]");

        String subTaskPrompt = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-subtask-suggestion.st");
        assertThat(subTaskPrompt).contains("金牌指导教练兼算法导师");
        assertThat(subTaskPrompt).contains("CORRECTION");
        assertThat(subTaskPrompt).contains("ADVANCEMENT");
        assertThat(subTaskPrompt).contains("actionPlanMarkdown");
        assertThat(subTaskPrompt).contains("[[questionNo]]");
        assertThat(subTaskPrompt).contains("[[targetSectionBlocksWithLatexAndHtml]]");

        String synthesizerPrompt = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase3-suggestion-synthesizer.st");
        assertThat(synthesizerPrompt).contains("评审组长兼论文终审编审专家");
        assertThat(synthesizerPrompt).contains("overallStrategy");
        assertThat(synthesizerPrompt).contains("topPriorities");
        assertThat(synthesizerPrompt).contains("GROUNDED_SUGGESTION_V3");
        assertThat(synthesizerPrompt).contains("[[allSubTaskSuggestionsJson]]");
    }
}
