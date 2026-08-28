package com.leetmodel.assistant.rag.chunk;

import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.CleanKnowledgeDocument;
import com.leetmodel.assistant.rag.source.KnowledgeDocument;
import com.leetmodel.assistant.rag.source.KnowledgeLoadResult;
import com.leetmodel.assistant.rag.source.KnowledgeSourceSelector;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeCleaner;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeLoader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChineseKnowledgeChunkerTest {

    private final ChineseTokenEstimator estimator = new ChineseTokenEstimator();

    @Test
    void keepsShortDocumentAsSingleChunk() {
        ChineseKnowledgeChunker chunker = chunker(8, 20, 30, 4, 200);

        List<KnowledgeChunk> chunks = chunker.chunk(document("【标题】\n\n这是一个短段落。"));

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.ordinal()).isZero();
            assertThat(chunk.content()).contains("【标题】", "短段落");
        });
    }

    @Test
    void splitsLongChineseAtStructureAndPunctuationWithOverlap() {
        ChineseKnowledgeChunker chunker = chunker(8, 20, 30, 4, 200);
        String content = "【方法】\n\n第一步建立变量并明确约束。第二步构造目标函数并求解！"
                + "第三步进行敏感性分析；第四步检查结果并解释。\n\n【结论】\n\n模型可以复现。";

        List<KnowledgeChunk> chunks = chunker.chunk(document(content));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.estimatedTokens()).isLessThanOrEqualTo(30);
            assertThat(chunk.content().length()).isLessThanOrEqualTo(200);
        });
        assertThat(chunks.get(1).content()).contains("步");
        assertThat(chunks.stream().map(KnowledgeChunk::content)).anyMatch(text -> text.contains("模型可以复现"));
    }

    @Test
    void preservesTablesAndCodeWhileRespectingEmbeddingLimit() {
        ChineseKnowledgeChunker chunker = chunker(6, 16, 24, 3, 80);
        String content = """
                【对比表】

                | 模型 | 用途 |
                | 线性规划 | 优化 |

                ```python
                result = solver.optimize(variables, constraints)
                print(result)
                ```

                最后验证结果。
                """;

        List<KnowledgeChunk> chunks = chunker.chunk(document(content));

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.estimatedTokens()).isLessThanOrEqualTo(24);
            assertThat(chunk.content().length()).isLessThanOrEqualTo(80);
        });
        String joined = chunks.stream().map(KnowledgeChunk::content).reduce("", (left, right) -> left + right);
        assertThat(joined).contains("| 模型 | 用途 |", "solver.optimize", "最后验证结果");
    }

    @Test
    void estimatorTreatsChinesePunctuationAndAsciiConservatively() {
        assertThat(estimator.estimate("中文，test1234！")).isEqualTo(6);
    }

    @Test
    void everyActualChunkStaysWithinEmbeddingLimits() {
        RagProperties properties = new RagProperties();
        KnowledgeSourceSelector selector = new KnowledgeSourceSelector(properties);
        Path root = locateActualKnowledgeBase();
        KnowledgeLoadResult loaded = new MarkdownKnowledgeLoader(selector).load(root, selector.select(root));
        MarkdownKnowledgeCleaner cleaner = new MarkdownKnowledgeCleaner();
        ChineseKnowledgeChunker chunker = new ChineseKnowledgeChunker(properties, estimator);

        List<KnowledgeChunk> chunks = loaded.documents().stream()
                .flatMap(document -> chunker.chunk(cleaner.clean(document)).stream()).toList();

        assertThat(chunks).isNotEmpty().allSatisfy(chunk -> {
            assertThat(chunk.estimatedTokens()).isLessThanOrEqualTo(properties.getChunkMaxTokens());
            assertThat(chunk.content().length()).isLessThanOrEqualTo(properties.getMaxEmbeddingInputChars());
        });
    }

    private ChineseKnowledgeChunker chunker(int min, int target, int max, int overlap, int maxChars) {
        RagProperties properties = new RagProperties();
        properties.setChunkMinTokens(min);
        properties.setChunkTargetTokens(target);
        properties.setChunkMaxTokens(max);
        properties.setChunkOverlapTokens(overlap);
        properties.setMaxEmbeddingInputChars(maxChars);
        return new ChineseKnowledgeChunker(properties, estimator);
    }

    private CleanKnowledgeDocument document(String content) {
        KnowledgeDocument source = new KnowledgeDocument("数学建模/测试.md", "测试", List.of(), null,
                List.of("数学建模"), "hash", Instant.EPOCH,
                content.getBytes(StandardCharsets.UTF_8).length, content);
        return new CleanKnowledgeDocument(source, content);
    }

    private Path locateActualKnowledgeBase() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("rag_kb");
            if (Files.isDirectory(candidate.resolve("数学建模"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位实际 rag_kb 目录");
    }
}
