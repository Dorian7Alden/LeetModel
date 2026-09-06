package com.leetmodel.knowledge.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRendererTest {

    @Test
    @DisplayName("加载并渲染 catalog-selection 提示词模板")
    void testRenderCatalogSelection() {
        String template = PromptTemplateRenderer.loadClasspathPrompt("prompts/catalog-selection.st");
        assertThat(template).isNotBlank();
        assertThat(template).contains("[[userQuery]]", "[[catalogManifest]]", "selectedPaths");

        Map<String, String> vars = Map.of(
                "userQuery", "求解非线性规划问题与灵敏度分析",
                "category", "优化类",
                "minSelection", "2",
                "maxSelection", "3",
                "catalogManifest", "数学建模/模型方法/优化模型与算法分类.md | 优化分类 | 求解器与算法"
        );

        String rendered = PromptTemplateRenderer.render(template, vars);

        assertThat(rendered).doesNotContain("[[userQuery]]");
        assertThat(rendered).contains("求解非线性规划问题与灵敏度分析");
        assertThat(rendered).contains("数学建模/模型方法/优化模型与算法分类.md");
    }

    @Test
    @DisplayName("变量值中包含特殊字符 $、\\、{} 时能安全字面量渲染，不抛异常")
    void testSpecialCharactersInVariables() {
        String template = "输入值: [[val]], 代码: [[code]]";
        Map<String, String> vars = Map.of(
                "val", "$100 \\frac{a}{b} \\alpha",
                "code", "{\"key\": \"value\"}"
        );

        String rendered = PromptTemplateRenderer.render(template, vars);

        assertThat(rendered).isEqualTo("输入值: $100 \\frac{a}{b} \\alpha, 代码: {\"key\": \"value\"}");
    }
}
