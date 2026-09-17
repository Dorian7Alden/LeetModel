package com.leetmodel.knowledge.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import com.leetmodel.knowledge.manifest.YamlKnowledgeManifestLoader;
import com.leetmodel.knowledge.manifest.dto.ReadmeYaml;
import com.leetmodel.knowledge.manifest.model.KnowledgeManifest;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 知识库自包含 ZIP 打包导出服务。
 * 导出包包含各级 Markdown 正文及自描述 README.yaml。
 */
@Slf4j
@Service
public class KnowledgeExportService {

    private final KnowledgeRetrievalProperties properties;
    private final YamlKnowledgeManifestLoader manifestLoader;
    private final ObjectMapper yamlMapper;

    public KnowledgeExportService(KnowledgeRetrievalProperties properties,
                                  YamlKnowledgeManifestLoader manifestLoader) {
        this.properties = properties;
        this.manifestLoader = manifestLoader;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public byte[] exportZip() throws IOException {
        Path root = Path.of(properties.getKnowledgeBasePath()).toAbsolutePath().normalize();
        KnowledgeManifest manifest = manifestLoader.loadRoot(root);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (ManifestDirectory dir : manifest.getDirectories()) {
                // 1. 导出或生成 README.yaml
                String yamlPath = dir.path().isEmpty() ? "README.yaml" : dir.path() + "/README.yaml";
                Path physicalYaml = root.resolve(yamlPath);
                byte[] yamlBytes;
                if (Files.isRegularFile(physicalYaml)) {
                    yamlBytes = Files.readAllBytes(physicalYaml);
                } else {
                    yamlBytes = serializeDirectoryToYaml(dir);
                }
                zos.putNextEntry(new ZipEntry(yamlPath));
                zos.write(yamlBytes);
                zos.closeEntry();

                // 2. 导出每个 Markdown 文件
                for (ManifestDocument doc : dir.documents()) {
                    Path physicalMd = root.resolve(doc.relativePath());
                    if (Files.isRegularFile(physicalMd)) {
                        zos.putNextEntry(new ZipEntry(doc.relativePath()));
                        zos.write(Files.readAllBytes(physicalMd));
                        zos.closeEntry();
                    }
                }
            }
            zos.finish();
        }
        log.info("成功完成知识库 ZIP 打包导出: size={} bytes, totalDocs={}", baos.size(), manifest.getAllDocuments().size());
        return baos.toByteArray();
    }

    private byte[] serializeDirectoryToYaml(ManifestDirectory dir) throws IOException {
        ReadmeYaml readme = new ReadmeYaml();
        readme.setSchemaVersion("v1.0");
        readme.setDirectoryName(dir.directoryName());
        readme.setPath(dir.path());
        readme.setTitle(dir.title());
        readme.setDescription(dir.description());
        readme.setTags(dir.tags());
        return yamlMapper.writeValueAsBytes(readme);
    }
}
