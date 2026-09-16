package com.leetmodel.review.workflow.v4;

import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.DeepEvidenceReviewV4Output;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewV4ReportAssemblerTest {

    @Test
    void buildsMarkdownEvidenceAndSortsIssuesBeforeStrengths() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .scoreNature("PLATFORM_TRAINING_SCORE")
                .dimensions(List.of(
                        DeepEvidenceReviewV3Output.V3ScoringDimension.builder()
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .dimensionName("数学形式化建模推导")
                                .score(BigDecimal.valueOf(20))
                                .maxScore(BigDecimal.valueOf(25))
                                .build()
                ))
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-S")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("STRENGTH")
                                .severity("LOW")
                                .statement("公式定义清楚且变量关系闭合。")
                                .scoreImpact("+0.0 分")
                                .blockId("B1")
                                .physicalPage(3)
                                .build(),
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-I")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("WEAKNESS")
                                .severity("HIGH")
                                .statement("公式中遗漏关键荷载参数，导致量纲不闭合。")
                                .scoreImpact("-2.0 分")
                                .blockId("B1")
                                .physicalPage(3)
                                .build()
                ))
                .requirementCoverage(List.of())
                .build();
        PaperDocumentV2 document = document();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                document,
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getWorkflowVersion()).isEqualTo("DEEP_EVIDENCE_REVIEW_V4");
        assertThat(output.getFindings()).extracting(DeepEvidenceReviewV4Output.Finding::getFindingId)
                .containsExactly("F-I", "F-S");
        assertThat(output.getFindings().get(0).getPriority()).isEqualTo("P1");
        assertThat(output.getFindings().get(1).getImportance()).isEqualTo("CORE");
        assertThat(output.getFindings()).extracting(DeepEvidenceReviewV4Output.Finding::getPhysicalPage)
                .containsOnly(3);
        assertThat(output.getFindings().get(0).getEvidenceQuotes().get(0).getQuoteMarkdown())
                .contains("$$", "V = KPL/H");
        assertThat(output.getOverallAssessmentMarkdown()).contains("值得保留", "当前优先问题");
    }

    @Test
    void rejectsReportWhenAllIssueEvidenceBlocksAreInvalid() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(60))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-I")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("ISSUE")
                                .severity("HIGH")
                                .statement("模型存在确定性问题。")
                                .blockId("NOT-EXIST")
                                .physicalPage(99)
                                .build()
                ))
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assembler.assemble(
                        source,
                        document(),
                        new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少可验证论文证据");
    }

    private PaperDocumentV2 document() {
        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        3,
                        100,
                        "测试论文",
                        "ZH",
                        "PAPER_PARSE_V2",
                        "2026-09-16T00:00:00Z"
                ),
                null,
                List.of(
                        new PaperDocumentV2.ContentBlockV2(
                                "B1",
                                PaperDocumentV2.BlockType.FORMULA,
                                3,
                                "$$V = KPL/H$$",
                                null,
                                new PaperDocumentV2.FormulaPayload("V = KPL/H", "(1)", false),
                                null,
                                null,
                                null,
                                List.of()
                        )
                ),
                List.of(new PaperDocumentV2.SectionIndex("SEC-1", "模型建立", 1, "B1", 3)),
                null
        );
    }
}
