package com.leetmodel.review.workflow.v3;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    @Test
    void shouldRenderSafelyWithLaTeXAndDollarSigns() {
        String template = "这是题型 [[category]] 的提示词：\n公式：[[formula]]\n金额：[[price]]\n总结：[[summary]]";
        Map<String, String> vars = Map.of(
                "category", "运筹优化类",
                "formula", "\\min \\sum_{i=1}^{n} c_{ij} x_{ij} + \\frac{\\alpha}{\\beta}",
                "price", "$100 和 $200",
                "summary", "纯文本 {含普通花括号} 不受影响"
        );

        String rendered = PromptTemplateRenderer.render(template, vars);

        assertThat(rendered).contains("\\min \\sum_{i=1}^{n} c_{ij} x_{ij} + \\frac{\\alpha}{\\beta}");
        assertThat(rendered).contains("$100 和 $200");
        assertThat(rendered).contains("{含普通花括号}");
        assertThat(rendered).doesNotContain("[[category]]");
    }

    @Test
    void shouldLoadClasspathPromptsWithoutError() {
        String p1 = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase1-structural-review.st");
        assertThat(p1).contains("ABSTRACT_STRUCTURE");
        assertThat(p1).contains("初筛 30 秒法则");
        assertThat(p1).contains("T7 摘要空洞无数值");

        String p2Planner = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-task-planner.st");
        assertThat(p2Planner).contains("ABSTRACT_VERIFICATION");
        assertThat(p2Planner).contains("专家评审规划心智模型");
        assertThat(p2Planner).contains("严防漏问底线");

        String p2Sub = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-subtask-evaluation.st");
        assertThat(p2Sub).contains("[[taskName]]");
        assertThat(p2Sub).contains("专家评审心智模型与思考维度");
        assertThat(p2Sub).contains("调包侠");
    }
}
