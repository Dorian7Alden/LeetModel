package com.leetmodel.assistant.rag.source;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 加载 Markdown 正文，并提取建立可追溯索引所需的元数据。 */
@Component
public class MarkdownKnowledgeLoader {

    private static final Pattern H1 = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final int MAX_YAML_CODE_POINTS = 64 * 1024;

    private final KnowledgeSourceSelector selector;

    public MarkdownKnowledgeLoader(KnowledgeSourceSelector selector) {
        this.selector = selector;
    }

    public KnowledgeLoadResult load() {
        Path root = Path.of(selectorPropertiesPath());
        return load(root, selector.select(root));
    }

    public KnowledgeLoadResult load(Path knowledgeBaseRoot, List<Path> relativePaths) {
        Path normalizedRoot = knowledgeBaseRoot.toAbsolutePath().normalize();
        List<KnowledgeDocument> documents = new ArrayList<>();
        List<KnowledgeLoadFailure> failures = new ArrayList<>();
        for (Path relativePath : relativePaths) {
            String displayPath = portablePath(relativePath.normalize());
            try {
                Path file = normalizedRoot.resolve(relativePath).normalize();
                if (!file.startsWith(normalizedRoot) || Files.isSymbolicLink(file)) {
                    throw new IllegalArgumentException("知识源路径越过根目录或为符号链接");
                }
                documents.add(loadOne(normalizedRoot, file));
            } catch (Exception exception) {
                failures.add(new KnowledgeLoadFailure(displayPath,
                        exception.getClass().getSimpleName(), safeMessage(exception)));
            }
        }
        return new KnowledgeLoadResult(documents, failures);
    }

    private KnowledgeDocument loadOne(Path root, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String content = decodeUtf8(bytes);
        Frontmatter frontmatter = parseFrontmatter(content);
        Path relativePath = root.relativize(file);
        String title = firstText(frontmatter.values().get("title"));
        if (title == null) {
            Matcher matcher = H1.matcher(frontmatter.body());
            title = matcher.find() ? matcher.group(1).trim() : stripMarkdownExtension(file.getFileName().toString());
        }
        return new KnowledgeDocument(
                portablePath(relativePath),
                title,
                tags(frontmatter.values().get("tags")),
                firstText(frontmatter.values().get("summary")),
                hierarchy(relativePath),
                sha256(bytes),
                Files.getLastModifiedTime(file).toInstant(),
                bytes.length,
                content);
    }

    private Frontmatter parseFrontmatter(String content) {
        String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
        if (!normalized.startsWith("---\n") && !normalized.startsWith("---\r\n")) {
            return new Frontmatter(Map.of(), normalized);
        }
        int firstLineEnd = normalized.indexOf('\n');
        int closingStart = findClosingDelimiter(normalized, firstLineEnd + 1);
        if (closingStart < 0) {
            throw new IllegalArgumentException("YAML frontmatter 缺少结束分隔符");
        }
        int closingEnd = normalized.indexOf('\n', closingStart);
        String yamlText = normalized.substring(firstLineEnd + 1, closingStart);
        String body = closingEnd < 0 ? "" : normalized.substring(closingEnd + 1);
        Object loaded = yaml().load(yamlText);
        if (loaded == null) {
            return new Frontmatter(Map.of(), body);
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("YAML frontmatter 必须是键值映射");
        }
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> values.put(String.valueOf(key).toLowerCase(Locale.ROOT), value));
        return new Frontmatter(Collections.unmodifiableMap(values), body);
    }

    private int findClosingDelimiter(String content, int fromIndex) {
        int position = fromIndex;
        while (position < content.length()) {
            int lineEnd = content.indexOf('\n', position);
            int end = lineEnd < 0 ? content.length() : lineEnd;
            String line = content.substring(position, end).stripTrailing();
            if ("---".equals(line)) {
                return position;
            }
            position = lineEnd < 0 ? content.length() : lineEnd + 1;
        }
        return -1;
    }

    private Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(10);
        options.setCodePointLimit(MAX_YAML_CODE_POINTS);
        return new Yaml(new SafeConstructor(options));
    }

    private List<String> tags(Object value) {
        if (value == null) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addTag(tags, item));
        } else {
            for (String item : String.valueOf(value).split(",")) {
                addTag(tags, item);
            }
        }
        return List.copyOf(tags);
    }

    private void addTag(Set<String> tags, Object value) {
        String tag = value == null ? "" : String.valueOf(value).trim();
        if (!tag.isEmpty()) {
            tags.add(tag);
        }
    }

    private List<String> hierarchy(Path relativePath) {
        List<String> hierarchy = new ArrayList<>();
        Path parent = relativePath.getParent();
        if (parent != null) {
            parent.forEach(segment -> hierarchy.add(segment.toString()));
        }
        return List.copyOf(hierarchy);
    }

    private String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }

    private String firstText(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String stripMarkdownExtension(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3) : fileName;
    }

    private String safeMessage(Exception exception) {
        if (exception instanceof CharacterCodingException) {
            return "文件不是有效 UTF-8";
        }
        if (exception instanceof YAMLException) {
            return "YAML frontmatter 格式错误";
        }
        if (exception instanceof IOException) {
            return "文件读取失败";
        }
        if (exception instanceof IllegalArgumentException && exception.getMessage() != null) {
            return exception.getMessage();
        }
        return "加载失败";
    }

    private String selectorPropertiesPath() {
        return selector.knowledgeBasePath();
    }

    private static String portablePath(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private record Frontmatter(Map<String, Object> values, String body) {
    }
}
