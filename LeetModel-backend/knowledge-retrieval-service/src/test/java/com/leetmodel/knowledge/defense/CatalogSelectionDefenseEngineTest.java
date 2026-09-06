package com.leetmodel.knowledge.defense;

import com.leetmodel.knowledge.defense.dto.DefenseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSelectionDefenseEngineTest {

    private CatalogSelectionDefenseEngine defenseEngine;
    private Set<String> validPaths;

    @BeforeEach
    void setUp() {
        DefensiveCatalogOutputParser parser = new DefensiveCatalogOutputParser();
        PathWhitelistValidator validator = new PathWhitelistValidator();
        SelectionCountTruncator truncator = new SelectionCountTruncator();
        GracefulFallbackProvider fallbackProvider = new GracefulFallbackProvider();
        defenseEngine = new CatalogSelectionDefenseEngine(parser, validator, truncator, fallbackProvider);

        validPaths = Set.of(
                "数学建模/模型方法/优化模型与算法分类.md",
                "数学建模/模型方法/常用模型速查-优化类.md",
                "数学建模/模型方法/常用模型速查-预测类.md",
                "数学建模/模型方法/常用模型速查-评价类.md",
                "数学建模/论文评审/评审板块/敏感性分析评审要点.md",
                "数学建模/题型方法/评价类题型特征.md"
        );
    }

    @Test
    @DisplayName("正常合规输出：通过全部4道防线，返回选拔结果")
    void testCleanOutputPassesAllGates() {
        String rawOutput = """
                {
                  "reasoning": "优化模型与算法分类覆盖了求解器与分类，搭配优化速查与敏感性分析",
                  "selectedPaths": [
                    "数学建模/模型方法/优化模型与算法分类.md",
                    "数学建模/模型方法/常用模型速查-优化类.md"
                  ]
                }
                """;

        DefenseResult result = defenseEngine.defend(rawOutput, "优化", 3, validPaths);

        assertThat(result.fallbackTriggered()).isFalse();
        assertThat(result.selectedPaths()).containsExactly(
                "数学建模/模型方法/优化模型与算法分类.md",
                "数学建模/模型方法/常用模型速查-优化类.md"
        );
        assertThat(result.reasoning()).contains("覆盖了求解器与分类");
    }

    @Test
    @DisplayName("防线1测试：自动剥离 Markdown 代码围栏与前后闲聊文字")
    void testMarkdownCodeFenceAndChatterStripped() {
        String rawOutput = """
                好的！这是为您精心挑选的参考文档：
                ```json
                {
                  "reasoning": "时序分析与预测",
                  "selectedPaths": [
                    "数学建模/模型方法/常用模型速查-预测类.md"
                  ]
                }
                ```
                希望能对您的建模有所帮助！
                """;

        DefenseResult result = defenseEngine.defend(rawOutput, "预测", 3, validPaths);

        assertThat(result.fallbackTriggered()).isFalse();
        assertThat(result.selectedPaths()).containsExactly("数学建模/模型方法/常用模型速查-预测类.md");
    }

    @Test
    @DisplayName("防线1测试：容错转义 LaTeX 公式反斜杠 \\frac、\\alpha，不破坏 JSON 反序列化")
    void testLatexBackslashTolerant() {
        String rawOutput = """
                {
                  "reasoning": "目标函数导数 \\frac{\\partial f}{\\partial x} 以及置信度 \\alpha \\le 0.05 需做敏感性分析",
                  "selectedPaths": [
                    "数学建模/论文评审/评审板块/敏感性分析评审要点.md"
                  ]
                }
                """;

        DefenseResult result = defenseEngine.defend(rawOutput, "优化", 3, validPaths);

        assertThat(result.fallbackTriggered()).isFalse();
        assertThat(result.selectedPaths()).containsExactly("数学建模/论文评审/评审板块/敏感性分析评审要点.md");
        assertThat(result.reasoning()).contains("敏感性分析");
    }

    @Test
    @DisplayName("防线2测试：绝对白名单校验剔除臆造与拼错路径")
    void testWhitelistFiltersHallucinations() {
        String rawOutput = """
                {
                  "reasoning": "测试白名单过滤",
                  "selectedPaths": [
                    "数学建模/模型方法/优化模型与算法分类.md",
                    "数学建模/模型方法/不存在的臆造文档.md",
                    "../etc/passwd"
                  ]
                }
                """;

        DefenseResult result = defenseEngine.defend(rawOutput, "优化", 3, validPaths);

        assertThat(result.fallbackTriggered()).isFalse();
        assertThat(result.selectedPaths()).containsExactly("数学建模/模型方法/优化模型与算法分类.md");
    }

    @Test
    @DisplayName("防线3测试：服务端硬截断超过 maxSelection 的项")
    void testHardCountTruncation() {
        String rawOutput = """
                {
                  "reasoning": "模型一次性选出了4篇",
                  "selectedPaths": [
                    "数学建模/模型方法/优化模型与算法分类.md",
                    "数学建模/模型方法/常用模型速查-优化类.md",
                    "数学建模/模型方法/常用模型速查-预测类.md",
                    "数学建模/论文评审/评审板块/敏感性分析评审要点.md"
                  ]
                }
                """;

        // 限制最多 2 篇
        DefenseResult result = defenseEngine.defend(rawOutput, "优化", 2, validPaths);

        assertThat(result.fallbackTriggered()).isFalse();
        assertThat(result.selectedPaths()).hasSize(2);
        assertThat(result.selectedPaths()).containsExactly(
                "数学建模/模型方法/优化模型与算法分类.md",
                "数学建模/模型方法/常用模型速查-优化类.md"
        );
    }

    @Test
    @DisplayName("防线4测试：当模型输出全部为非法路径时，优雅降级至分类基准文档")
    void testFallbackWhenAllPathsHallucinated() {
        String rawOutput = """
                {
                  "reasoning": "全部都是臆造路径",
                  "selectedPaths": [
                    "虚构/路径1.md",
                    "虚构/路径2.md"
                  ]
                }
                """;

        DefenseResult result = defenseEngine.defend(rawOutput, "优化", 3, validPaths);

        assertThat(result.fallbackTriggered()).isTrue();
        assertThat(result.selectedPaths()).isNotEmpty();
        assertThat(result.selectedPaths()).allMatch(validPaths::contains);
        assertThat(result.fallbackReason()).isEqualTo("WHITELIST_FILTERED_ALL");
    }

    @Test
    @DisplayName("防线4测试：当模型输出损坏无法解析时，优雅降级至分类基准文档")
    void testFallbackWhenJsonMalformed() {
        String rawOutput = "这不是合法的 JSON 格式，这是模型的闲聊废话";

        DefenseResult result = defenseEngine.defend(rawOutput, "评价", 3, validPaths);

        assertThat(result.fallbackTriggered()).isTrue();
        assertThat(result.selectedPaths()).isNotEmpty();
        assertThat(result.selectedPaths()).contains("数学建模/模型方法/常用模型速查-评价类.md");
    }

    @Test
    @DisplayName("防线4测试：直接超时降级接口正常运行")
    void testDirectFallbackOnTimeout() {
        DefenseResult result = defenseEngine.fallbackOnly("预测", validPaths, "网关超时 504");

        assertThat(result.fallbackTriggered()).isTrue();
        assertThat(result.selectedPaths()).contains("数学建模/模型方法/常用模型速查-预测类.md");
        assertThat(result.fallbackReason()).isEqualTo("网关超时 504");
    }
}
