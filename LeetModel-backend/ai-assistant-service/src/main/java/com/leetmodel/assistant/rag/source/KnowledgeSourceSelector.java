package com.leetmodel.assistant.rag.source;

import com.leetmodel.assistant.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** 按白名单生成 RAG V1 的确定性 Markdown 文件清单。 */
@Component
public class KnowledgeSourceSelector {

    static final Path INCLUDED_ROOT = Path.of("数学建模");
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".kb", ".claude", "data", "docs", "scripts", ".git", "数模评审参考资料");
    private static final Set<String> EXCLUDED_FILES = Set.of("readme.md", "context.md");

    private final RagProperties properties;

    public KnowledgeSourceSelector(RagProperties properties) {
        this.properties = properties;
    }

    public List<Path> select() {
        return select(Path.of(properties.getKnowledgeBasePath()));
    }

    /**
     * 返回相对于知识库根目录的路径，使用统一排序保证重复构建得到相同清单。
     */
    public List<Path> select(Path knowledgeBaseRoot) {
        Path normalizedRoot = knowledgeBaseRoot.toAbsolutePath().normalize();
        Path includedRoot = normalizedRoot.resolve(INCLUDED_ROOT).normalize();
        if (!includedRoot.startsWith(normalizedRoot) || !Files.isDirectory(includedRoot)) {
            throw new IllegalArgumentException("RAG 知识源目录不存在: " + INCLUDED_ROOT);
        }
        try (Stream<Path> files = Files.walk(includedRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .map(normalizedRoot::relativize)
                    .filter(this::isIncludedMarkdown)
                    .sorted(Comparator.comparing(KnowledgeSourceSelector::portablePath))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("无法生成 RAG 知识源清单", exception);
        }
    }

    private boolean isIncludedMarkdown(Path relativePath) {
        String fileName = relativePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".md") || EXCLUDED_FILES.contains(fileName)) {
            return false;
        }
        for (Path segment : relativePath) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static String portablePath(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }
}
