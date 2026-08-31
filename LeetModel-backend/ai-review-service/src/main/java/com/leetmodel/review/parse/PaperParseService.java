package com.leetmodel.review.parse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.review.entity.PaperParseArtifact;
import com.leetmodel.review.mapper.PaperParseArtifactMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 下载不可变提交并生成、复用具体版本的解析产物。 */
@Service
public class PaperParseService {
    private final PaperParseArtifactMapper mapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final StorageService storageService;
    private final PaperParseV1Parser parser;
    private final ObjectMapper objectMapper;

    public PaperParseService(PaperParseArtifactMapper mapper,
                             SubmissionFeignClient submissionFeignClient,
                             StorageService storageService,
                             PaperParseV1Parser parser,
                             ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.submissionFeignClient = submissionFeignClient;
        this.storageService = storageService;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    public synchronized PaperParseDTO ensure(Long submissionId, String workflowVersion) {
        if (!PaperParseV1Parser.WORKFLOW_VERSION.equals(workflowVersion)) {
            throw new IllegalArgumentException("未知 PDF 解析版本: " + workflowVersion);
        }
        PaperParseArtifact reusable = mapper.selectOne(new LambdaQueryWrapper<PaperParseArtifact>()
                .eq(PaperParseArtifact::getSubmissionId, submissionId)
                .eq(PaperParseArtifact::getWorkflowVersion, workflowVersion)
                .eq(PaperParseArtifact::getSchemaVersion, PaperParseV1Parser.SCHEMA_VERSION)
                .in(PaperParseArtifact::getStatus, "SUCCESS", "PARTIAL_SUCCESS")
                .orderByDesc(PaperParseArtifact::getCreateTime)
                .last("LIMIT 1"));
        if (reusable != null) return toDTO(reusable);

        SubmissionReviewDTO submission = requiredSubmission(submissionId);
        PaperParseArtifact artifact = new PaperParseArtifact();
        artifact.setSubmissionId(submissionId);
        artifact.setWorkflowVersion(workflowVersion);
        artifact.setSchemaVersion(PaperParseV1Parser.SCHEMA_VERSION);
        try (InputStream input = storageService.download(submission.getObjectName())) {
            byte[] pdf = input.readAllBytes();
            if (pdf.length == 0) throw new IllegalArgumentException("提交 PDF 为空");
            artifact.setContentSha256(sha256(pdf));
            PaperDocumentV1 document = parser.parse(submissionId, pdf);
            artifact.setStatus(document.quality().status());
            artifact.setPageCount(document.pageCount());
            artifact.setTruncated(document.truncated());
            artifact.setQualityJson(objectMapper.writeValueAsString(document.quality()));
            artifact.setDocumentJson(objectMapper.writeValueAsString(document));
            mapper.insert(artifact);
            return toDTO(artifact);
        } catch (Exception exception) {
            artifact.setContentSha256(artifact.getContentSha256() == null
                    ? sha256((submissionId + ":unavailable").getBytes(StandardCharsets.UTF_8))
                    : artifact.getContentSha256());
            artifact.setStatus("FAILED");
            artifact.setTruncated(false);
            artifact.setErrorMessage(truncate(exception.getMessage()));
            mapper.insert(artifact);
            throw new IllegalStateException("PAPER_PARSE_V1 解析失败: " + artifact.getErrorMessage(), exception);
        }
    }

    public PaperParseArtifact requiredArtifact(Long artifactId) {
        PaperParseArtifact artifact = mapper.selectById(artifactId);
        if (artifact == null || !("SUCCESS".equals(artifact.getStatus())
                || "PARTIAL_SUCCESS".equals(artifact.getStatus()))) {
            throw new IllegalArgumentException("PDF 解析产物不存在或不可用");
        }
        return artifact;
    }

    public PaperDocumentV1 readDocument(PaperParseArtifact artifact) {
        try {
            return objectMapper.readValue(artifact.getDocumentJson(), PaperDocumentV1.class);
        } catch (Exception exception) {
            throw new IllegalStateException("PAPER_DOCUMENT_V1 产物无法读取", exception);
        }
    }

    private SubmissionReviewDTO requiredSubmission(Long submissionId) {
        Result<SubmissionReviewDTO> result = submissionFeignClient.getForReview(submissionId);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("submission-service 暂不可用");
        }
        return result.getData();
    }

    private PaperParseDTO toDTO(PaperParseArtifact artifact) {
        return new PaperParseDTO(artifact.getId(), artifact.getSubmissionId(),
                artifact.getWorkflowVersion(), artifact.getSchemaVersion(), artifact.getContentSha256(),
                artifact.getStatus(), artifact.getPageCount(), artifact.getTruncated(),
                artifact.getQualityJson(), artifact.getDocumentJson(), artifact.getErrorMessage());
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 PDF 摘要", exception);
        }
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) return "未知解析错误";
        return message.substring(0, Math.min(message.length(), 500));
    }
}
