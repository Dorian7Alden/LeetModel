package com.leetmodel.assistant.rag.source;

import com.leetmodel.assistant.rag.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSourceSelectorTest {

    @TempDir
    Path tempDir;

    @Test
    void includesOnlyDeclaredContentMarkdownInStableOrder() throws IOException {
        write("数学建模/z-last.md");
        write("数学建模/模型方法/a-first.MD");
        write("数学建模/README.md");
        write("数学建模/模型方法/README.md");
        write("数学建模/CONTEXT.md");
        write("数学建模/docs/hidden.md");
        write("数学建模/data/hidden.md");
        write("数学建模/file.pdf");
        write("未声明目录/should-not-index.md");
        write("数模评审参考资料/should-not-index.md");

        List<Path> selected = selector().select(tempDir);

        assertThat(selected).extracting(KnowledgeSourceSelectorTest::portablePath)
                .containsExactly("数学建模/z-last.md", "数学建模/模型方法/a-first.MD");
    }

    @Test
    void actualKnowledgeBaseProducesDeterministicManifest() {
        Path actualRoot = locateActualKnowledgeBase();

        List<Path> first = selector().select(actualRoot);
        List<Path> second = selector().select(actualRoot);

        assertThat(second).isEqualTo(first);
        assertThat(first).hasSize(73);
        assertThat(first).noneMatch(path -> path.getFileName().toString().equalsIgnoreCase("README.md"));
        assertThat(first).allMatch(path -> portablePath(path).startsWith("数学建模/"));
    }

    private KnowledgeSourceSelector selector() {
        return new KnowledgeSourceSelector(new RagProperties());
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

    private void write(String relativePath) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");
    }

    private static String portablePath(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }
}
