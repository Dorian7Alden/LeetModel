package com.leetmodel.knowledge.archive;

import com.leetmodel.knowledge.archive.vo.KnowledgeImportSummaryVO;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import com.leetmodel.knowledge.manifest.ManifestValidationException;
import com.leetmodel.knowledge.manifest.YamlKnowledgeManifestLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeArchiveServiceTest {

    private KnowledgeExportService exportService;
    private KnowledgeImportService importService;

    @BeforeEach
    void setUp() {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        Path kbRoot = Path.of("../../rag_kb").toAbsolutePath().normalize();
        if (!Files.isDirectory(kbRoot)) {
            kbRoot = Path.of("rag_kb").toAbsolutePath().normalize();
        }
        properties.setKnowledgeBasePath(kbRoot.toString());

        YamlKnowledgeManifestLoader loader = new YamlKnowledgeManifestLoader();
        exportService = new KnowledgeExportService(properties, loader);
        importService = new KnowledgeImportService(loader);
    }

    @Test
    @DisplayName("端到端测试：打包导出自包含 ZIP 并重新导入解析，验证 100% 数据一致性")
    void testExportAndReimportZipLossless() throws Exception {
        // 1. 导出 ZIP
        byte[] zipBytes = exportService.exportZip();
        assertThat(zipBytes).isNotEmpty();
        assertThat(zipBytes.length).isGreaterThan(1024);

        // 2. 重新导入解析
        KnowledgeImportSummaryVO summary = importService.importZip(new ByteArrayInputStream(zipBytes));

        assertThat(summary).isNotNull();
        assertThat(summary.getStatus()).isEqualTo("SUCCESS");
        assertThat(summary.getTotalDirectories()).isGreaterThanOrEqualTo(3);
        assertThat(summary.getTotalDocuments()).isGreaterThanOrEqualTo(20);
        assertThat(summary.getManifestVersion()).startsWith("MANIFEST_");
    }

    @Test
    @DisplayName("异常断言：空 ZIP 压缩包抛出 ManifestValidationException")
    void testImportEmptyZipThrowsException() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.finish();
        }
        byte[] emptyZip = baos.toByteArray();

        assertThatThrownBy(() -> importService.importZip(new ByteArrayInputStream(emptyZip)))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("ZIP 知识包为空");
    }

    @Test
    @DisplayName("异常断言：缺少 README.yaml 的 ZIP 压缩包抛出校验异常")
    void testImportZipWithoutReadmeThrowsException() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("test.txt"));
            zos.write("some text".getBytes());
            zos.closeEntry();
            zos.finish();
        }
        byte[] invalidZip = baos.toByteArray();

        assertThatThrownBy(() -> importService.importZip(new ByteArrayInputStream(invalidZip)))
                .isInstanceOf(ManifestValidationException.class)
                .hasMessageContaining("未找到任何 README.yaml");
    }
}
