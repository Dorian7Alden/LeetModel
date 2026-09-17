package com.leetmodel.review.parse.v2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperParseV2QualityGateTest {

    private final PaperParseV2QualityGate qualityGate = new PaperParseV2QualityGate();

    @Test
    void shouldAcceptDocumentWithCompletePageCoverage() {
        PaperDocumentV2 document = documentWithPages(1, 2, 3, 4, 5);

        qualityGate.validate(document);

        assertThat(qualityGate.isReusable(document)).isTrue();
    }

    @Test
    void shouldRejectDocumentWhenFirstPageIsMissing() {
        PaperDocumentV2 document = documentWithPages(2, 3, 4, 5);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> qualityGate.validate(document)
        );

        assertThat(error.getMessage()).startsWith("PAPER_PARSE_V2_INCOMPLETE_COVERAGE");
        assertThat(qualityGate.isReusable(document)).isFalse();
    }

    @Test
    void shouldRejectDocumentWhenMissingPagesExceedFivePercent() {
        List<Integer> pages = new ArrayList<>();
        for (int page = 1; page <= 31; page++) {
            if (page == 2 || page == 27) continue;
            pages.add(page);
        }
        PaperDocumentV2 document = documentWithPages(pages.stream().mapToInt(Integer::intValue).toArray());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> qualityGate.validate(document)
        );

        assertThat(error.getMessage()).contains("coveredPages=29/31");
    }

    private PaperDocumentV2 documentWithPages(int... coveredPages) {
        int totalPages = 0;
        List<PaperDocumentV2.ContentBlockV2> blocks = new ArrayList<>();
        for (int page : coveredPages) {
            totalPages = Math.max(totalPages, page);
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
        if (coveredPages.length == 4 && coveredPages[0] == 2) {
            totalPages = 5;
        }
        if (coveredPages.length == 29) {
            totalPages = 31;
        }
        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        totalPages,
                        100,
                        "测试论文",
                        "ZH",
                        PaperDocumentV2.WORKFLOW_VERSION,
                        "2026-09-16T10:00:00Z"
                ),
                new PaperDocumentV2.LayoutAesthetics(80.0, "HIGH", "GOOD", "良好"),
                List.copyOf(blocks),
                List.of(),
                new PaperDocumentV2.DocumentQualityV2(
                        "SUCCESS",
                        coveredPages.length,
                        totalPages - coveredPages.length,
                        0,
                        0,
                        0,
                        0.0,
                        List.of()
                )
        );
    }
}
