package com.leetmodel.knowledge.archive;

import com.leetmodel.knowledge.archive.vo.KnowledgeImportSummaryVO;
import com.leetmodel.knowledge.manifest.ManifestValidationException;
import com.leetmodel.knowledge.manifest.YamlKnowledgeManifestLoader;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 知识库自包含 ZIP 无损解析与导入服务。
 * 内存流式解压、README.yaml Schema 强校验、文档存在性核验与 Manifest 重建。
 */
@Slf4j
@Service
public class KnowledgeImportService {

    private final YamlKnowledgeManifestLoader manifestLoader;

    public KnowledgeImportService(YamlKnowledgeManifestLoader manifestLoader) {
        this.manifestLoader = manifestLoader;
    }

    public KnowledgeImportSummaryVO importZip(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new ManifestValidationException("导入文件流为空");
        }

        // 1. 内存流式解包全部文件
        Map<String, byte[]> zipEntries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String normalizedPath = entry.getName().replace('\\', '/').replaceAll("^/+", "");
                if (normalizedPath.contains("..")) {
                    throw new ManifestValidationException("ZIP 包内包含非法路径穿透: " + normalizedPath);
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                zipEntries.put(normalizedPath, baos.toByteArray());
                zis.closeEntry();
            }
        }

        if (zipEntries.isEmpty()) {
            throw new ManifestValidationException("ZIP 知识包为空或无效压缩文件");
        }

        // 2. 扫描并解析全部 README.yaml
        List<ManifestDirectory> parsedDirectories = new ArrayList<>();
        int totalTags = 0;

        for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
            String path = entry.getKey();
            if (path.endsWith("README.yaml")) {
                String yamlContent = new String(entry.getValue(), StandardCharsets.UTF_8);
                String dirPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
                String expectedDirName = dirPath.contains("/") ? dirPath.substring(dirPath.lastIndexOf('/') + 1) : dirPath;

                ManifestDirectory directory = manifestLoader.loadYamlString(yamlContent, expectedDirName.isEmpty() ? null : expectedDirName, null, null);

                // 校验声明的每个 Markdown 文件是否存在于压缩包中
                for (ManifestDocument doc : directory.documents()) {
                    String expectedDocPath = dirPath.isEmpty() ? doc.file() : dirPath + "/" + doc.file();
                    if (!zipEntries.containsKey(expectedDocPath)) {
                        throw new ManifestValidationException("ZIP 包内缺失 README.yaml 声明的文档: " + expectedDocPath);
                    }
                    totalTags += doc.compositeTags().size();
                }
                parsedDirectories.add(directory);
            }
        }

        if (parsedDirectories.isEmpty()) {
            throw new ManifestValidationException("ZIP 知识包内未找到任何 README.yaml 元数据描述文件");
        }

        // 3. 计算导入后的 Manifest 版本
        String manifestVersion = computeManifestVersion(parsedDirectories);
        int totalDocuments = parsedDirectories.stream().mapToInt(d -> d.documents().size()).sum();

        log.info("成功完成知识库 ZIP 无损导入解析: directories={}, documents={}, tags={}, version={}",
                parsedDirectories.size(), totalDocuments, totalTags, manifestVersion);

        return new KnowledgeImportSummaryVO(
                parsedDirectories.size(),
                totalDocuments,
                totalTags,
                manifestVersion,
                "SUCCESS",
                "知识包自包含无损解析完成，共导入 " + totalDocuments + " 篇文档"
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
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return "MANIFEST_" + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "MANIFEST_" + Integer.toHexString(sb.toString().hashCode());
        }
    }
}
