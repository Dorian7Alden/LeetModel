package com.leetmodel.assistant.rag.source;

import com.leetmodel.assistant.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownKnowledgeLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTraceableMetadataAndHandlesMissingFields() throws IOException {
        write("数学建模/模型方法/规划.md", """
                ---
                tags: [优化, 线性规划, 优化]
                summary: 一个可检索摘要
                ---
                # 线性规划

                正文。
                """);
        write("数学建模/比赛经验/无元数据.md", "没有一级标题");
        List<Path> manifest = selector().select(tempDir);

        KnowledgeLoadResult result = loader().load(tempDir, manifest);

        assertThat(result.successful()).isTrue();
        assertThat(result.documents()).hasSize(2);
        KnowledgeDocument document = result.documents().stream()
                .filter(item -> item.relativePath().endsWith("规划.md")).findFirst().orElseThrow();
        assertThat(document.title()).isEqualTo("线性规划");
        assertThat(document.tags()).containsExactly("优化", "线性规划");
        assertThat(document.summary()).isEqualTo("一个可检索摘要");
        assertThat(document.hierarchy()).containsExactly("数学建模", "模型方法");
        assertThat(document.contentHash()).matches("[0-9a-f]{64}");
        assertThat(document.byteSize()).isPositive();
        assertThat(document.lastModifiedAt()).isNotNull();
        assertThat(result.documents()).anySatisfy(item -> {
            if (item.relativePath().endsWith("无元数据.md")) {
                assertThat(item.title()).isEqualTo("无元数据");
                assertThat(item.tags()).isEmpty();
                assertThat(item.summary()).isNull();
            }
        });
    }

    @Test
    void reportsBadFileWithoutPollutingSuccessfulDocuments() throws IOException {
        write("数学建模/正常.md", "# 正常文档\n正文");
        write("数学建模/坏文件.md", "---\ntags: [未闭合\n---\n# 坏文件");
        List<Path> manifest = selector().select(tempDir);

        KnowledgeLoadResult result = loader().load(tempDir, manifest);

        assertThat(result.successful()).isFalse();
        assertThat(result.documents()).singleElement()
                .satisfies(document -> assertThat(document.relativePath()).endsWith("正常.md"));
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.relativePath()).endsWith("坏文件.md");
            assertThat(failure.errorType()).isNotBlank();
            assertThat(failure.message()).doesNotContain(tempDir.toString(), "# 坏文件");
        });
    }

    @Test
    void loadsAllActualContentDocumentsWithoutFailures() {
        Path root = locateActualKnowledgeBase();
        KnowledgeSourceSelector selector = selector();

        KnowledgeLoadResult result = loader().load(root, selector.select(root));

        assertThat(result.failures()).isEmpty();
        assertThat(result.documents()).hasSize(73);
        assertThat(result.documents()).allSatisfy(document -> {
            assertThat(document.relativePath()).startsWith("数学建模/");
            assertThat(document.title()).isNotBlank();
            assertThat(document.contentHash()).matches("[0-9a-f]{64}");
        });
    }

    private MarkdownKnowledgeLoader loader() {
        return new MarkdownKnowledgeLoader(selector());
    }

    private KnowledgeSourceSelector selector() {
        return new KnowledgeSourceSelector(new RagProperties());
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
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
