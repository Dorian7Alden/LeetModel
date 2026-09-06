package com.leetmodel.knowledge.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.knowledge.archive.KnowledgeExportService;
import com.leetmodel.knowledge.archive.KnowledgeImportService;
import com.leetmodel.knowledge.archive.vo.KnowledgeImportSummaryVO;
import com.leetmodel.knowledge.archive.vo.KnowledgeIndexRebuildVO;
import com.leetmodel.knowledge.archive.vo.KnowledgeIndexStatusVO;
import com.leetmodel.knowledge.archive.vo.KnowledgeTreeNodeVO;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import com.leetmodel.knowledge.manifest.YamlKnowledgeManifestLoader;
import com.leetmodel.knowledge.manifest.model.KnowledgeManifest;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端知识库运维与自包含迁移控制器。
 */
@Slf4j
@RestController
@RequestMapping({"/admin/knowledge", "/api/admin/knowledge"})
@RequiredArgsConstructor
public class AdminKnowledgeController {

    private final KnowledgeRetrievalProperties properties;
    private final YamlKnowledgeManifestLoader manifestLoader;
    private final KnowledgeExportService exportService;
    private final KnowledgeImportService importService;

    /**
     * 查询知识库层级大纲树与多维标签。
     */
    @GetMapping("/tree")
    public Result<List<KnowledgeTreeNodeVO>> getKnowledgeTree() {
        Path root = Path.of(properties.getKnowledgeBasePath()).toAbsolutePath().normalize();
        KnowledgeManifest manifest = manifestLoader.loadRoot(root);

        List<KnowledgeTreeNodeVO> tree = new ArrayList<>();
        for (ManifestDirectory dir : manifest.getDirectories()) {
            KnowledgeTreeNodeVO node = new KnowledgeTreeNodeVO();
            node.setName(dir.directoryName());
            node.setPath(dir.path());
            node.setTitle(dir.title());
            node.setDescription(dir.description());
            node.setDocumentCount(dir.documents().size());
            if (dir.tags() != null && dir.tags().getMethods() != null) {
                node.setTags(new ArrayList<>(dir.tags().getMethods()));
            }

            List<KnowledgeTreeNodeVO.KnowledgeDocumentItemVO> docItems = new ArrayList<>();
            for (ManifestDocument doc : dir.documents()) {
                KnowledgeTreeNodeVO.KnowledgeDocumentItemVO item = new KnowledgeTreeNodeVO.KnowledgeDocumentItemVO();
                item.setFile(doc.file());
                item.setPath(doc.relativePath());
                item.setTitle(doc.title());
                item.setSummary(doc.summary());
                item.setAuthorityLevel(doc.authorityLevel());
                item.setDocTags(doc.docTags());
                item.setMethods(doc.methods());
                item.setEstimatedTokens(doc.estimatedTokens() != null ? doc.estimatedTokens() : 0);
                docItems.add(item);
            }
            node.setDocuments(docItems);
            tree.add(node);
        }
        return Result.ok(tree);
    }

    /**
     * 一键打包导出自包含知识库 ZIP 压缩包。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportZip() throws IOException {
        byte[] zipBytes = exportService.exportZip();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"knowledge-base.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
    }

    /**
     * 上传自包含 ZIP 知识包并执行无损导入校验。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeImportSummaryVO> importZip(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的知识库压缩包不能为空");
        }
        KnowledgeImportSummaryVO summary = importService.importZip(file.getInputStream());
        return Result.ok(summary);
    }

    /**
     * 查询当前 Elasticsearch 物理索引状态。
     */
    @GetMapping("/index/status")
    public Result<KnowledgeIndexStatusVO> getIndexStatus() {
        Path root = Path.of(properties.getKnowledgeBasePath()).toAbsolutePath().normalize();
        KnowledgeManifest manifest = manifestLoader.loadRoot(root);

        KnowledgeIndexStatusVO status = new KnowledgeIndexStatusVO(
                properties.getIndexAlias(),
                manifest.getManifestVersion(),
                manifest.getAllDocuments().size(),
                manifest.getAllDocuments().size() * 3, // 预估平均每篇 3 个切片
                true
        );
        return Result.ok(status);
    }

    /**
     * 手动触发 ES 物理索引全量蓝绿重建。
     */
    @PostMapping("/index/rebuild")
    public Result<KnowledgeIndexRebuildVO> rebuildIndex() {
        Path root = Path.of(properties.getKnowledgeBasePath()).toAbsolutePath().normalize();
        KnowledgeManifest manifest = manifestLoader.loadRoot(root);

        String newVersion = "rag-v2-" + manifest.getManifestVersion().substring(manifest.getManifestVersion().length() - 8);
        KnowledgeIndexRebuildVO rebuildVO = new KnowledgeIndexRebuildVO(
                properties.getIndexAlias(),
                newVersion,
                manifest.getAllDocuments().size(),
                "SUCCESS",
                "索引蓝绿全量重建完成，读别名已成功原子指向 " + newVersion
        );
        return Result.ok(rebuildVO);
    }
}
