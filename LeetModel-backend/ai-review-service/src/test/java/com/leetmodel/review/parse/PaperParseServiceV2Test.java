package com.leetmodel.review.parse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.api.feign.FileContentClient;
import com.leetmodel.review.entity.PaperParseArtifact;
import com.leetmodel.review.mapper.PaperParseArtifactMapper;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import com.leetmodel.review.parse.v2.PaperParseV2Parser;
import com.leetmodel.review.parse.v2.PaperParseV2Properties;
import com.leetmodel.review.parse.v2.PaperParseV2QualityGate;
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
    private FileContentClient fileContentClient;
    private PaperParseV1Parser v1Parser;
    private PaperParseV2Parser v2Parser;
    private PaperParseV2Properties v2Properties;
    private PaperParseV2QualityGate v2QualityGate;
    private ObjectMapper objectMapper;
    private PaperParseService parseService;

    @BeforeEach
    void setUp() {
        mapper = mock(PaperParseArtifactMapper.class);
        submissionFeignClient = mock(SubmissionFeignClient.class);
        fileContentClient = mock(FileContentClient.class);
        v1Parser = mock(PaperParseV1Parser.class);
        v2Parser = mock(PaperParseV2Parser.class);
        v2Properties = new PaperParseV2Properties();
        v2QualityGate = new PaperParseV2QualityGate();
        objectMapper = new ObjectMapper();
        parseService = new PaperParseService(
                mapper,
                submissionFeignClient,
                fileContentClient,
                v1Parser,
                v2Parser,
                v2Properties,
                v2QualityGate,
                objectMapper
        );
    }

    @Test
    void shouldEnsureV2ParseAndPersistArtifact() throws Exception {
        Long submissionId = 5001L;
        SubmissionReviewDTO sub = new SubmissionReviewDTO(submissionId, 1L, 1L, 1, 9001L);
        when(submissionFeignClient.getForReview(submissionId)).thenReturn(Result.ok(sub));

        byte[] pdfBytes = "fake pdf content".getBytes();
        when(fileContentClient.open(9001L)).thenReturn(new ByteArrayInputStream(pdfBytes));

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
        PaperDocumentV2 reusableDocument = documentWithCoveredPages(submissionId, 5);
        try {
            reusable.setDocumentJson(objectMapper.writeValueAsString(reusableDocument));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        reusable.setQualityJson("{}");

        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(reusable);

        PaperParseDTO dto = parseService.ensure(submissionId, "PAPER_PARSE_V2");
        assertThat(dto).isNotNull();
        assertThat(dto.getArtifactId()).isEqualTo(99L);
        assertThat(dto.getWorkflowVersion()).isEqualTo("PAPER_PARSE_V2");
        org.mockito.Mockito.verifyNoInteractions(fileContentClient);
    }

    @Test
    void shouldReparseWhenExistingArtifactMissesFirstPage() throws Exception {
        Long submissionId = 5005L;
        PaperParseArtifact incomplete = new PaperParseArtifact();
        incomplete.setId(100L);
        incomplete.setSubmissionId(submissionId);
        incomplete.setWorkflowVersion(PaperDocumentV2.WORKFLOW_VERSION);
        incomplete.setSchemaVersion(PaperDocumentV2.SCHEMA_VERSION);
        incomplete.setStatus("PARTIAL_SUCCESS");
        incomplete.setPageCount(3);
        incomplete.setDocumentJson(objectMapper.writeValueAsString(documentMissingFirstPage(submissionId)));
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(incomplete);

        SubmissionReviewDTO submission = new SubmissionReviewDTO(
                submissionId,
                1L,
                1L,
                1,
                9002L
        );
        when(submissionFeignClient.getForReview(submissionId)).thenReturn(Result.ok(submission));
        when(fileContentClient.open(9002L))
                .thenReturn(new ByteArrayInputStream("new pdf".getBytes()));

        PaperDocumentV2 reparsed = documentWithCoveredPages(submissionId, 3);
        when(v2Parser.parse(any(), any(), any())).thenReturn(reparsed);

        PaperParseDTO result = parseService.ensure(submissionId, PaperDocumentV2.WORKFLOW_VERSION);

        assertThat(result.getArtifactId()).isNull();
        verify(fileContentClient).open(9002L);
        verify(v2Parser).parse(any(), any(), any());
        verify(mapper).insert(any(PaperParseArtifact.class));
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

    private PaperDocumentV2 documentWithCoveredPages(Long submissionId, int totalPages) {
        List<PaperDocumentV2.ContentBlockV2> blocks = new java.util.ArrayList<>();
        for (int page = 1; page <= totalPages; page++) {
            blocks.add(new PaperDocumentV2.ContentBlockV2(
                    "B" + page,
                    PaperDocumentV2.BlockType.PARAGRAPH,
                    page,
                    "第 " + page + " 页正文",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            ));
        }
        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                submissionId,
                "sha256-covered",
                new PaperDocumentV2.DocumentMetadata(
                        totalPages,
                        500,
                        "测试论文",
                        "ZH",
                        PaperDocumentV2.WORKFLOW_VERSION,
                        "2026-09-16T10:00:00Z"
                ),
                new PaperDocumentV2.LayoutAesthetics(88.0, "HIGH", "GOOD", "良好"),
                List.copyOf(blocks),
                List.of(),
                new PaperDocumentV2.DocumentQualityV2(
                        "SUCCESS",
                        totalPages,
                        0,
                        0,
                        0,
                        0,
                        0.0,
                        List.of()
                )
        );
    }

    private PaperDocumentV2 documentMissingFirstPage(Long submissionId) {
        PaperDocumentV2 covered = documentWithCoveredPages(submissionId, 3);
        return new PaperDocumentV2(
                covered.schemaVersion(),
                covered.submissionId(),
                covered.contentSha256(),
                covered.metadata(),
                covered.layoutAesthetics(),
                covered.blocks().subList(1, covered.blocks().size()),
                covered.sections(),
                new PaperDocumentV2.DocumentQualityV2(
                        "PARTIAL_SUCCESS",
                        2,
                        1,
                        0,
                        0,
                        0,
                        0.0,
                        List.of("BLANK_OR_SCANNED_PAGES_PRESENT")
                )
        );
    }
}
