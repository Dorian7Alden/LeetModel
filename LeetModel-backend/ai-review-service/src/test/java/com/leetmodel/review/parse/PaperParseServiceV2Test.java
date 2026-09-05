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
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import com.leetmodel.review.parse.v2.PaperParseV2Parser;
import com.leetmodel.review.parse.v2.PaperParseV2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperParseServiceV2Test {

    private PaperParseArtifactMapper mapper;
    private SubmissionFeignClient submissionFeignClient;
    private StorageService storageService;
    private PaperParseV1Parser v1Parser;
    private PaperParseV2Parser v2Parser;
    private PaperParseV2Properties v2Properties;
    private ObjectMapper objectMapper;
    private PaperParseService parseService;

    @BeforeEach
    void setUp() {
        mapper = mock(PaperParseArtifactMapper.class);
        submissionFeignClient = mock(SubmissionFeignClient.class);
        storageService = mock(StorageService.class);
        v1Parser = mock(PaperParseV1Parser.class);
        v2Parser = mock(PaperParseV2Parser.class);
        v2Properties = new PaperParseV2Properties();
        objectMapper = new ObjectMapper();
        parseService = new PaperParseService(
                mapper,
                submissionFeignClient,
                storageService,
                v1Parser,
                v2Parser,
                v2Properties,
                objectMapper
        );
    }

    @Test
    void shouldEnsureV2ParseAndPersistArtifact() throws Exception {
        Long submissionId = 5001L;
        SubmissionReviewDTO sub = new SubmissionReviewDTO(submissionId, 1L, 1L, 1, "test-v2.pdf");
        when(submissionFeignClient.getForReview(submissionId)).thenReturn(Result.ok(sub));

        byte[] pdfBytes = "fake pdf content".getBytes();
        when(storageService.download("test-v2.pdf")).thenReturn(new ByteArrayInputStream(pdfBytes));

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                submissionId,
                "sha256-mock",
                new PaperDocumentV2.DocumentMetadata(2, 500, "测试论文", "ZH", "PAPER_PARSE_V2", "2026-09-05T10:00:00Z"),
                new PaperDocumentV2.LayoutAesthetics(88.0, "HIGH", "EXCELLENT", "良好"),
                List.of(new PaperDocumentV2.ContentBlockV2("B1", PaperDocumentV2.BlockType.PARAGRAPH, 1, "正文", null, null, null, null, null, List.of())),
                List.of(),
                new PaperDocumentV2.DocumentQualityV2("SUCCESS", 2, 0, 0, 0, 0, 0.0, List.of())
        );
        when(v2Parser.parse(any(), any(), any())).thenReturn(doc);

        PaperParseDTO dto = parseService.ensure(submissionId, "PAPER_PARSE_V2");

        assertThat(dto).isNotNull();
        assertThat(dto.getWorkflowVersion()).isEqualTo("PAPER_PARSE_V2");
        assertThat(dto.getSchemaVersion()).isEqualTo("PAPER_DOCUMENT_V2");
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");
        assertThat(dto.getPageCount()).isEqualTo(2);

        ArgumentCaptor<PaperParseArtifact> captor = ArgumentCaptor.forClass(PaperParseArtifact.class);
        verify(mapper).insert(captor.capture());
        PaperParseArtifact saved = captor.getValue();
        assertThat(saved.getWorkflowVersion()).isEqualTo("PAPER_PARSE_V2");
        assertThat(saved.getSchemaVersion()).isEqualTo("PAPER_DOCUMENT_V2");
        assertThat(saved.getDocumentJson()).contains("B1");
    }

    @Test
    void shouldReuseExistingV2ArtifactWhenPresentInDatabase() {
        Long submissionId = 5002L;
        PaperParseArtifact reusable = new PaperParseArtifact();
        reusable.setId(99L);
        reusable.setSubmissionId(submissionId);
        reusable.setWorkflowVersion("PAPER_PARSE_V2");
        reusable.setSchemaVersion("PAPER_DOCUMENT_V2");
        reusable.setStatus("SUCCESS");
        reusable.setPageCount(5);
        reusable.setDocumentJson("{}");
        reusable.setQualityJson("{}");

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(reusable);

        PaperParseDTO dto = parseService.ensure(submissionId, "PAPER_PARSE_V2");
        assertThat(dto).isNotNull();
        assertThat(dto.getArtifactId()).isEqualTo(99L);
        assertThat(dto.getWorkflowVersion()).isEqualTo("PAPER_PARSE_V2");
        verify(storageService, never()).download(any());
    }

    @Test
    void shouldRejectUnknownWorkflowVersion() {
        assertThrows(IllegalArgumentException.class, () ->
                parseService.ensure(5003L, "UNKNOWN_VERSION"));
    }

    @Test
    void shouldDeserializePaperDocumentV2() throws Exception {
        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                5004L,
                "sha256-read",
                new PaperDocumentV2.DocumentMetadata(1, 100, "测试", "ZH", "PAPER_PARSE_V2", "2026-09-05T10:00:00Z"),
                new PaperDocumentV2.LayoutAesthetics(90.0, "HIGH", "EXCELLENT", "良好"),
                List.of(new PaperDocumentV2.ContentBlockV2("B1", PaperDocumentV2.BlockType.PARAGRAPH, 1, "内容", null, null, null, null, null, List.of())),
                List.of(),
                new PaperDocumentV2.DocumentQualityV2("SUCCESS", 1, 0, 0, 0, 0, 0.0, List.of())
        );
        PaperParseArtifact artifact = new PaperParseArtifact();
        artifact.setDocumentJson(objectMapper.writeValueAsString(doc));

        PaperDocumentV2 read = parseService.readDocumentV2(artifact);
        assertThat(read).isNotNull();
        assertThat(read.submissionId()).isEqualTo(5004L);
        assertThat(read.blocks()).hasSize(1);
        assertThat(read.blocks().get(0).blockId()).isEqualTo("B1");
    }
}
