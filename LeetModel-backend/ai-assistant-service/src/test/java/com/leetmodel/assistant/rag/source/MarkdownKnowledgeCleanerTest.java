package com.leetmodel.assistant.rag.source;

import com.leetmodel.assistant.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownKnowledgeCleanerTest {

    private final MarkdownKnowledgeCleaner cleaner = new MarkdownKnowledgeCleaner();

    @ParameterizedTest
    @ValueSource(strings = {"模型方法", "比赛经验", "论文评审"})
    void matchesStructuredCleaningSnapshot(String category) throws IOException {
        String input = resource(category + ".md");
        String expected = resource(category + ".snapshot.txt").strip();
        KnowledgeDocument document = document("数学建模/" + category + "/示例.md", input);

        CleanKnowledgeDocument cleaned = cleaner.clean(document);

        assertThat(cleaned.content()).isEqualTo(expected);
        assertThat(cleaned.sourceCitation()).isEqualTo("数学建模/" + category + "/示例.md");
        assertThat(cleaned.content()).doesNotContain("tags:", "/home/dorian", "C:\\Users");
    }

    @Test
    void cleansEveryActualKnowledgeDocumentWithRelativeSources() {
        Path root = locateActualKnowledgeBase();
        KnowledgeSourceSelector selector = new KnowledgeSourceSelector(new RagProperties());
        KnowledgeLoadResult loaded = new MarkdownKnowledgeLoader(selector).load(root, selector.select(root));

        assertThat(loaded.documents()).hasSize(73).allSatisfy(document -> {
            CleanKnowledgeDocument cleaned = cleaner.clean(document);
            assertThat(cleaned.content()).isNotBlank();
            assertThat(cleaned.sourceCitation()).startsWith("数学建模/").doesNotStartWith("/");
        });
    }

    private KnowledgeDocument document(String path, String content) {
        return new KnowledgeDocument(path, "示例", List.of(), null, List.of("数学建模"),
                "hash", Instant.EPOCH, content.getBytes(StandardCharsets.UTF_8).length, content);
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/rag-cleaner-snapshots/" + name)) {
            if (stream == null) {
                throw new IOException("测试资源不存在: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
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
