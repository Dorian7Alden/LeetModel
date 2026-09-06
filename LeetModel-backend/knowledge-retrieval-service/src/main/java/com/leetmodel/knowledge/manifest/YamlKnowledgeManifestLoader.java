package com.leetmodel.knowledge.manifest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.leetmodel.knowledge.manifest.dto.DirectoryTagsYaml;
import com.leetmodel.knowledge.manifest.dto.DocumentYaml;
import com.leetmodel.knowledge.manifest.dto.ReadmeYaml;
import com.leetmodel.knowledge.manifest.model.KnowledgeManifest;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * README.yaml 规范解析与内存 Manifest 动态装载器。
 */
@Slf4j
@Component
public class YamlKnowledgeManifestLoader {

    public static final String SUPPORTED_SCHEMA_VERSION = "v1.0";
    public static final String DEFAULT_AUTHORITY_LEVEL = "L4";
    public static final String README_YAML_NAME = "README.yaml";

    private final ObjectMapper yamlMapper;

    public YamlKnowledgeManifestLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 扫描知识库根目录下的全部 README.yaml 并构建全局统一 Manifest。
     */
    public KnowledgeManifest loadRoot(Path rootPath) {
        if (rootPath == null || !Files.isDirectory(rootPath)) {
            throw new ManifestValidationException("知识库根路径无效: " + rootPath);
        }
        Path normalizedRoot = rootPath.toAbsolutePath().normalize();
        List<ManifestDirectory> directories = new ArrayList<>();
        java.util.Set<Path> managedDirs = new java.util.HashSet<>();

        try (Stream<Path> walk = Files.walk(normalizedRoot)) {
            List<Path> allFiles = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            // 1. 优先装载包含 README.yaml 的自描述目录
            for (Path file : allFiles) {
                if (README_YAML_NAME.equals(file.getFileName().toString())) {
                    Path dirPath = file.getParent();
                    ManifestDirectory directory = loadDirectory(dirPath, normalizedRoot);
                    directories.add(directory);
                    managedDirs.add(dirPath);
                }
            }

            // 2. 兜底自动发现未配置 README.yaml 但包含 .md 的目录
            java.util.Map<Path, List<Path>> unmanaged = new java.util.LinkedHashMap<>();
            for (Path file : allFiles) {
                String name = file.getFileName().toString();
                if (name.endsWith(".md") && !"README.md".equals(name)) {
                    Path dir = file.getParent();
                    if (!managedDirs.contains(dir)) {
                        unmanaged.computeIfAbsent(dir, k -> new ArrayList<>()).add(file);
                    }
                }
            }

            for (java.util.Map.Entry<Path, List<Path>> entry : unmanaged.entrySet()) {
                directories.add(autoDiscoverDirectory(entry.getKey(), entry.getValue(), normalizedRoot));
            }
        } catch (IOException e) {
            throw new ManifestValidationException("扫描知识库目录失败: " + e.getMessage(), e);
        }

        String manifestVersion = computeManifestVersion(directories);
        log.info("成功装载知识库 Manifest: directories={}, totalDocs={}, version={}",
                directories.size(),
                directories.stream().mapToInt(d -> d.documents().size()).sum(),
                manifestVersion);

        return new KnowledgeManifest(manifestVersion, directories);
    }

    private ManifestDirectory autoDiscoverDirectory(Path dirPath, List<Path> mdFiles, Path rootPath) {
        String dirName = dirPath.getFileName().toString();
        String relDirPath = rootPath.relativize(dirPath).toString().replace('\\', '/');
        List<ManifestDocument> docs = new ArrayList<>();

        for (Path mdFile : mdFiles) {
            String fileName = mdFile.getFileName().toString();
            String docRelPath = relDirPath.isEmpty() ? fileName : relDirPath + "/" + fileName;
            String content = "";
            try {
                content = Files.readString(mdFile, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
            String title = fileName.replaceFirst("\\.md$", "");
            String summary = extractSummaryFromContent(content);
            String authLevel = determineAuthorityLevel(docRelPath);

            docs.add(new ManifestDocument(
                    docRelPath,
                    fileName,
                    title,
                    summary,
                    List.of(),
                    List.of(),
                    List.of(dirName),
                    authLevel,
                    Math.max(1, content.length() / 2),
                    dirName,
                    relDirPath
            ));
        }
        return new ManifestDirectory(dirName, relDirPath, dirName, "", new DirectoryTagsYaml(), docs);
    }

    private String extractSummaryFromContent(String text) {
        if (text == null || text.isBlank()) return "数学建模知识文档";
        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                for (String line : text.substring(3, end).split("\\R")) {
                    int colon = line.indexOf(':');
                    if (colon > 0 && "summary".equals(line.substring(0, colon).trim())) {
                        return line.substring(colon + 1).trim();
                    }
                }
            }
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.replaceFirst("^#+\\s*", "").trim();
            if (!trimmed.isBlank() && !"---".equals(trimmed) && !trimmed.contains(":")) {
                return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
            }
        }
        return "数学建模知识文档";
    }

    private String determineAuthorityLevel(String path) {
        if (path.contains("论文评审/评审板块/") || path.contains("论文评审/评审视角/")) return "L3";
        if (path.contains("题型方法/") || path.contains("模型方法/")) return "L4";
        return "L5";
    }

    /**
     * 解析指定目录下的 README.yaml。
     */
    public ManifestDirectory loadDirectory(Path dirPath, Path rootPath) {
        if (dirPath == null || !Files.isDirectory(dirPath)) {
            throw new ManifestValidationException("目标目录不存在: " + dirPath);
        }
        Path yamlPath = dirPath.resolve(README_YAML_NAME);
        if (!Files.isRegularFile(yamlPath)) {
            throw new ManifestValidationException("目录缺少 README.yaml: " + dirPath);
        }

        try {
            String content = Files.readString(yamlPath, StandardCharsets.UTF_8);
            String expectedDirName = dirPath.getFileName().toString();
            return loadYamlString(content, expectedDirName, dirPath, rootPath);
        } catch (IOException e) {
            throw new ManifestValidationException("读取 README.yaml 失败: " + yamlPath, e);
        }
    }

    /**
     * 解析 YAML 文本字符串并执行严格 Schema 校验与标签继承聚合。
     */
    public ManifestDirectory loadYamlString(String yamlContent, String expectedDirName,
                                            Path baseDirOrNull, Path rootPathOrNull) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new ManifestValidationException("README.yaml 内容为空");
        }

        // 1. 反序列化
        ReadmeYaml readme;
        try {
            readme = yamlMapper.readValue(yamlContent, ReadmeYaml.class);
        } catch (Exception e) {
            throw new ManifestValidationException("README.yaml 语法解析失败: " + e.getMessage(), e);
        }

        // 2. 根字段必填与版本强校验
        validateRootFields(readme, expectedDirName);

        // 3. 计算目录标准化路径
        String directoryPath = resolveDirectoryPath(readme, baseDirOrNull, rootPathOrNull);

        // 4. 解析并继承处理每个文档
        DirectoryTagsYaml dirTags = readme.getTags() != null ? readme.getTags() : new DirectoryTagsYaml();
        List<ManifestDocument> documents = new ArrayList<>();

        if (readme.getDocuments() != null) {
            for (DocumentYaml docYaml : readme.getDocuments()) {
                ManifestDocument doc = processDocument(docYaml, readme.getDirectoryName(),
                        directoryPath, dirTags, baseDirOrNull, rootPathOrNull);
                documents.add(doc);
            }
        }

        return new ManifestDirectory(
                readme.getDirectoryName(),
                directoryPath,
                readme.getTitle(),
                readme.getDescription(),
                dirTags,
                documents
        );
    }

    private void validateRootFields(ReadmeYaml readme, String expectedDirName) {
        if (readme == null) {
            throw new ManifestValidationException("README.yaml 实体解析为空");
        }
        if (readme.getSchemaVersion() == null || !SUPPORTED_SCHEMA_VERSION.equals(readme.getSchemaVersion().trim())) {
            throw new ManifestValidationException("不支持的 schema_version: " + readme.getSchemaVersion()
                    + "，当前版本固定为 " + SUPPORTED_SCHEMA_VERSION);
        }
        if (readme.getDirectoryName() == null || readme.getDirectoryName().isBlank()) {
            throw new ManifestValidationException("directory_name 字段不能为空");
        }
        if (expectedDirName != null && !expectedDirName.isBlank()
                && !expectedDirName.equals(readme.getDirectoryName().trim())) {
            throw new ManifestValidationException("directory_name 不匹配: 实际目录名=" + expectedDirName
                    + ", yaml声明=" + readme.getDirectoryName());
        }
        if (readme.getTitle() == null || readme.getTitle().isBlank()) {
            throw new ManifestValidationException("title 目录标题不能为空");
        }
    }

    private String resolveDirectoryPath(ReadmeYaml readme, Path baseDirOrNull, Path rootPathOrNull) {
        if (baseDirOrNull != null && rootPathOrNull != null) {
            Path normalizedBase = baseDirOrNull.toAbsolutePath().normalize();
            Path normalizedRoot = rootPathOrNull.toAbsolutePath().normalize();
            if (normalizedBase.startsWith(normalizedRoot)) {
                return normalizedRoot.relativize(normalizedBase).toString().replace('\\', '/');
            }
        }
        if (readme.getPath() != null && !readme.getPath().isBlank()) {
            return readme.getPath().trim().replace('\\', '/');
        }
        return readme.getDirectoryName().trim();
    }

    private ManifestDocument processDocument(DocumentYaml docYaml, String dirName,
                                             String dirPath, DirectoryTagsYaml dirTags,
                                             Path baseDirOrNull, Path rootPathOrNull) {
        // 1. 文档必填字段与防路径穿透校验
        if (docYaml.getFile() == null || docYaml.getFile().isBlank()) {
            throw new ManifestValidationException("文档 file 字段不能为空");
        }
        String fileName = docYaml.getFile().trim();
        if (fileName.contains("..") || fileName.startsWith("/") || fileName.startsWith("\\")) {
            throw new ManifestValidationException("文档文件路径非法，禁止路径穿透: " + fileName);
        }
        if (docYaml.getTitle() == null || docYaml.getTitle().isBlank()) {
            throw new ManifestValidationException("文档 title 不能为空: " + fileName);
        }
        if (docYaml.getSummary() == null || docYaml.getSummary().isBlank()) {
            throw new ManifestValidationException("文档 summary 不能为空: " + fileName);
        }

        // 2. 物理文件存在性校验 (当提供物理路径时)
        if (baseDirOrNull != null) {
            Path filePath = baseDirOrNull.resolve(fileName).normalize();
            if (!Files.isRegularFile(filePath)) {
                throw new ManifestValidationException("未找到声明的 Markdown 文件: " + filePath);
            }
        }

        // 3. 计算相对知识库根目录的标准化路径
        String docRelativePath;
        if (dirPath == null || dirPath.isBlank()) {
            docRelativePath = fileName;
        } else {
            docRelativePath = dirPath.replaceAll("/+$", "") + "/" + fileName;
        }

        // 4. 权威层级继承与覆盖逻辑
        String authorityLevel = docYaml.getAuthorityLevel();
        if (authorityLevel == null || authorityLevel.isBlank()) {
            authorityLevel = dirTags.getAuthorityLevel();
        }
        if (authorityLevel == null || authorityLevel.isBlank()) {
            authorityLevel = DEFAULT_AUTHORITY_LEVEL;
        }

        // 5. 算法方法合并逻辑 (并集去重)
        Set<String> methodSet = new LinkedHashSet<>();
        if (dirTags.getMethods() != null) {
            dirTags.getMethods().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(methodSet::add);
        }
        if (docYaml.getMethods() != null) {
            docYaml.getMethods().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(methodSet::add);
        }
        List<String> mergedMethods = List.copyOf(methodSet);

        // 6. 全局复合多维标签聚拢
        Set<String> compositeTagSet = new LinkedHashSet<>();
        if (dirTags.getContest() != null && !dirTags.getContest().isBlank()) {
            compositeTagSet.add(dirTags.getContest().trim());
        }
        if (dirTags.getYear() != null) {
            compositeTagSet.add(String.valueOf(dirTags.getYear()));
        }
        if (dirTags.getProblem() != null && !dirTags.getProblem().isBlank()) {
            compositeTagSet.add(dirTags.getProblem().trim());
        }
        if (dirTags.getPrize() != null && !dirTags.getPrize().isBlank()) {
            compositeTagSet.add(dirTags.getPrize().trim());
        }
        if (dirTags.getProblemType() != null && !dirTags.getProblemType().isBlank()) {
            compositeTagSet.add(dirTags.getProblemType().trim());
        }
        compositeTagSet.addAll(mergedMethods);
        if (docYaml.getDocTags() != null) {
            docYaml.getDocTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(compositeTagSet::add);
        }
        List<String> compositeTags = List.copyOf(compositeTagSet);

        return new ManifestDocument(
                docRelativePath,
                fileName,
                docYaml.getTitle().trim(),
                docYaml.getSummary().trim(),
                docYaml.getDocTags() != null ? List.copyOf(docYaml.getDocTags()) : List.of(),
                mergedMethods,
                compositeTags,
                authorityLevel.trim(),
                docYaml.getEstimatedTokens(),
                dirName,
                dirPath
        );
    }

    private String computeManifestVersion(List<ManifestDirectory> directories) {
        StringBuilder sb = new StringBuilder();
        directories.stream()
                .flatMap(d -> d.documents().stream())
                .sorted(Comparator.comparing(ManifestDocument::relativePath))
                .forEach(doc -> {
                    sb.append(doc.relativePath()).append('\0')
                            .append(doc.title()).append('\0')
                            .append(doc.summary()).append('\n');
                });
        return "MANIFEST_" + sha256(sb.toString()).substring(0, 16);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
