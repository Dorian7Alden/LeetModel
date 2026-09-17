package com.leetmodel.knowledge.manifest;

import com.leetmodel.knowledge.manifest.model.KnowledgeManifest;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlKnowledgeManifestLoaderTest {

    private final YamlKnowledgeManifestLoader loader = new YamlKnowledgeManifestLoader();

    @Test
    @DisplayName("实地测试：加载数学建模模型方法目录 README.yaml 并验证属性继承")
    void testLoadModelMethodsDirectory() {
        Path kbRoot = Path.of("../../rag_kb").toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(kbRoot)) {
            kbRoot = Path.of("rag_kb").toAbsolutePath().normalize();
        }
        Path targetDir = kbRoot.resolve("数学建模/模型方法");

        ManifestDirectory dir = loader.loadDirectory(targetDir, kbRoot);

        assertThat(dir).isNotNull();
        assertThat(dir.directoryName()).isEqualTo("模型方法");
        assertThat(dir.title()).isEqualTo("数学建模核心模型与算法库");
        assertThat(dir.documents()).isNotEmpty();

        ManifestDocument doc = dir.documents().stream()
                .filter(d -> d.file().equals("优化模型与算法分类.md"))
                .findFirst()
                .orElseThrow();

        assertThat(doc.title()).isEqualTo("优化模型与算法分类全景");
        assertThat(doc.authorityLevel()).isEqualTo("L4");
        assertThat(doc.methods()).contains("非线性规划", "智能优化算法");
        assertThat(doc.compositeTags()).contains("通用", "综合方法", "非线性规划", "优化模型");
    }

    @Test
    @DisplayName("实地测试：扫描根目录构建全局 Manifest 并验证白名单与版本号")
    void testLoadRootManifest() {
        Path kbRoot = Path.of("../../rag_kb").toAbsolutePath().normalize();
        if (!java.nio.file.Files.isDirectory(kbRoot)) {
            kbRoot = Path.of("rag_kb").toAbsolutePath().normalize();
        }

        KnowledgeManifest manifest = loader.loadRoot(kbRoot);

        assertThat(manifest).isNotNull();
        assertThat(manifest.getManifestVersion()).startsWith("MANIFEST_");
        assertThat(manifest.getDirectories()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(manifest.getAllDocuments()).isNotEmpty();

        String samplePath = "数学建模/模型方法/常用模型速查-优化类.md";
        assertThat(manifest.isValidPath(samplePath)).isTrue();
        assertThat(manifest.isValidPath("数学建模/不存在的文件.md")).isFalse();
        assertThat(manifest.findByPath(samplePath)).isNotNull();
    }

    @Test
    @DisplayName("标签继承与覆盖：文档覆盖目录级权威级别，合并方法清单")
    void testTagInheritanceAndOverride() {
        String yaml = """
                schema_version: "v1.0"
                directory_name: "test_dir"
                title: "测试目录"
                tags:
                  contest: "国赛"
                  year: 2024
                  authority_level: "L4"
                  methods:
                    - "基础规划"
                documents:
                  - file: "doc1.md"
                    title: "文档1"
                    summary: "这是摘要"
                    authority_level: "L2"
                    methods:
                      - "智能优化"
                    doc_tags:
                      - "特别标签"
                """;

        ManifestDirectory dir = loader.loadYamlString(yaml, "test_dir", null, null);
        assertThat(dir.documents()).hasSize(1);

        ManifestDocument doc = dir.documents().get(0);
        assertThat(doc.authorityLevel()).isEqualTo("L2");
        assertThat(doc.methods()).containsExactly("基础规划", "智能优化");
        assertThat(doc.compositeTags()).contains("国赛", "2024", "基础规划", "智能优化", "特别标签");
    }

    @Test
    @DisplayName("异常断言：缺失 schema_version 抛出 ManifestValidationException")
    void testMissingSchemaVersion() {
        String yaml = """
                directory_name: "test_dir"
                title: "测试目录"
                """;

        assertThatThrownBy(() -> loader.loadYamlString(yaml, "test_dir", null, null))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("不支持的 schema_version");
    }

    @Test
    @DisplayName("异常断言：schema_version 不兼容抛出异常")
    void testUnsupportedSchemaVersion() {
        String yaml = """
                schema_version: "v2.0"
                directory_name: "test_dir"
                title: "测试目录"
                """;

        assertThatThrownBy(() -> loader.loadYamlString(yaml, "test_dir", null, null))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("不支持的 schema_version: v2.0");
    }

    @Test
    @DisplayName("异常断言：目录名与物理文件夹名不一致抛出异常")
    void testDirectoryNameMismatch() {
        String yaml = """
                schema_version: "v1.0"
                directory_name: "declared_name"
                title: "测试目录"
                """;

        assertThatThrownBy(() -> loader.loadYamlString(yaml, "actual_name", null, null))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("directory_name 不匹配");
    }

    @Test
    @DisplayName("异常断言：文档尝试路径穿透抛出异常")
    void testPathTraversalAttempt() {
        String yaml = """
                schema_version: "v1.0"
                directory_name: "test_dir"
                title: "测试目录"
                documents:
                  - file: "../secret.md"
                    title: "穿透文档"
                    summary: "非法路径摘要"
                """;

        assertThatThrownBy(() -> loader.loadYamlString(yaml, "test_dir", null, null))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("禁止路径穿透");
    }

    @Test
    @DisplayName("异常断言：缺少必填文档 summary 抛出异常")
    void testMissingSummary() {
        String yaml = """
                schema_version: "v1.0"
                directory_name: "test_dir"
                title: "测试目录"
                documents:
                  - file: "doc.md"
                    title: "无摘要文档"
                """;

        assertThatThrownBy(() -> loader.loadYamlString(yaml, "test_dir", null, null))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("summary 不能为空");
    }
}
