package com.leetmodel.review.parse.v2;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentFlattenerTest {

    private DiscrepancyArbiter arbiter;
    private PdfBoxTextExtractor textExtractor;
    private DocumentFlattener flattener;

    @BeforeEach
    void setUp() {
        arbiter = mock(DiscrepancyArbiter.class);
        textExtractor = mock(PdfBoxTextExtractor.class);
        flattener = new DocumentFlattener(arbiter, textExtractor);
    }

    @Test
    void shouldFlattenSinglePageDocument() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            WindowBlockDTO heading = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.HEADING,
                    1,
                    "一、问题重述",
                    new PaperDocumentV2.HeadingPayload(1, "一、", "问题重述"),
                    null, null, null, null, List.of()
            );
            WindowBlockDTO paragraph = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.PARAGRAPH,
                    1,
                    "正文第一段内容。",
                    null, null, null, null, null, List.of()
            );
            WindowChunkDTO chunk1 = new WindowChunkDTO(
                    1, 1, 1, false, false,
                    new WindowChunkDTO.WindowLayoutAesthetics(90.0, "HIGH", "排版规整"),
                    List.of(heading, paragraph)
            );

            PaperDocumentV2 doc = flattener.flatten(
                    2001L,
                    "sha256-test",
                    1,
                    List.of(chunk1),
                    document,
                    false
            );

            assertThat(doc).isNotNull();
            assertThat(doc.schemaVersion()).isEqualTo(PaperDocumentV2.SCHEMA_VERSION);
            assertThat(doc.submissionId()).isEqualTo(2001L);
            assertThat(doc.metadata().totalPages()).isEqualTo(1);
            assertThat(doc.metadata().paperTitle()).isEqualTo("问题重述");
            assertThat(doc.blocks()).hasSize(2);
            assertThat(doc.blocks().get(0).blockId()).isEqualTo("B1");
            assertThat(doc.blocks().get(1).blockId()).isEqualTo("B2");
            assertThat(doc.sections()).hasSize(1);
            assertThat(doc.sections().get(0).sectionId()).isEqualTo("SEC-1");
            assertThat(doc.quality().status()).isEqualTo("SUCCESS");
            assertThat(doc.quality().readablePages()).isEqualTo(1);
            assertThat(doc.layoutAesthetics().overallScore()).isEqualTo(90.0);
        }
    }

    @Test
    void shouldFlattenMultiPageDocumentWithArbitration() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.addPage(new PDPage());

            // Page 1 block
            WindowBlockDTO p1 = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.HEADING,
                    1,
                    "一、背景",
                    new PaperDocumentV2.HeadingPayload(1, "一、", "背景"),
                    null, null, null, null, List.of()
            );
            // Page 2 in window 1
            WindowBlockDTO p2_vA = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.PARAGRAPH,
                    2,
                    "第二页内容版本 A",
                    null, null, null, null, null, List.of()
            );
            WindowChunkDTO chunk1 = new WindowChunkDTO(
                    1, 1, 2, false, true,
                    new WindowChunkDTO.WindowLayoutAesthetics(85.0, "HIGH", "良好"),
                    List.of(p1, p2_vA)
            );

            // Page 2 in window 2
            WindowBlockDTO p2_vB = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.PARAGRAPH,
                    2,
                    "第二页内容版本 B",
                    null, null, null, null, null, List.of()
            );
            // Page 3 in window 2
            WindowBlockDTO p3 = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.FORMULA,
                    3,
                    "$$y = ax + b$$",
                    null,
                    new PaperDocumentV2.FormulaPayload("y = ax + b", "(1)", false),
                    null, null, null, List.of()
            );
            WindowChunkDTO chunk2 = new WindowChunkDTO(
                    2, 2, 3, true, false,
                    new WindowChunkDTO.WindowLayoutAesthetics(87.0, "HIGH", "充实"),
                    List.of(p2_vB, p3)
            );

            // Arbiter returns arbitrated block for page 2
            WindowBlockDTO p2_arbitrated = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.PARAGRAPH,
                    2,
                    "第二页最终仲裁内容",
                    null, null, null, null, null, List.of()
            );
            when(arbiter.arbitrate(any(), any(), anyInt(), any(), anyLong()))
                    .thenReturn(List.of(p2_arbitrated));

            PaperDocumentV2 doc = flattener.flatten(
                    2002L,
                    "sha256-multi",
                    3,
                    List.of(chunk1, chunk2),
                    document,
                    false
            );

            assertThat(doc).isNotNull();
            assertThat(doc.metadata().totalPages()).isEqualTo(3);
            assertThat(doc.blocks()).hasSize(3);
            assertThat(doc.blocks().get(0).blockId()).isEqualTo("B1");
            assertThat(doc.blocks().get(0).physicalPage()).isEqualTo(1);
            assertThat(doc.blocks().get(1).blockId()).isEqualTo("B2");
            assertThat(doc.blocks().get(1).physicalPage()).isEqualTo(2);
            assertThat(doc.blocks().get(1).text()).isEqualTo("第二页最终仲裁内容");
            assertThat(doc.blocks().get(2).blockId()).isEqualTo("B3");
            assertThat(doc.blocks().get(2).physicalPage()).isEqualTo(3);
            assertThat(doc.quality().status()).isEqualTo("SUCCESS");
            assertThat(doc.quality().formulaCount()).isEqualTo(1);
            assertThat(doc.layoutAesthetics().overallScore()).isEqualTo(86.0);
        }
    }

    @Test
    void shouldMarkPartialSuccessWhenDegradedOcrIsPresent() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            WindowBlockDTO paragraph = new WindowBlockDTO(
                    PaperDocumentV2.BlockType.PARAGRAPH,
                    1,
                    "降级提取的文本",
                    null, null, null, null, null, List.of()
            );
            WindowChunkDTO chunk = new WindowChunkDTO(
                    1, 1, 1, false, false,
                    new WindowChunkDTO.WindowLayoutAesthetics(60.0, "MEDIUM", "降级兜底"),
                    List.of(paragraph)
            );

            PaperDocumentV2 doc = flattener.flatten(
                    2003L,
                    "sha256-degraded",
                    1,
                    List.of(chunk),
                    document,
                    true
            );

            assertThat(doc.quality().status()).isEqualTo("PARTIAL_SUCCESS");
            assertThat(doc.quality().warnings()).contains("DEGRADED_LOCAL_OCR_PRESENT");
        }
    }
}
