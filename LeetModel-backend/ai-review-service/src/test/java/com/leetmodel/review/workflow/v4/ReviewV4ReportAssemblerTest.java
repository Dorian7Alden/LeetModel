package com.leetmodel.review.workflow.v4;

import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.DeepEvidenceReviewV4Output;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Test
    void keepsCompleteEvidenceAndCorrectsNegativeStrengthWithExplicitBlockReference() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        String longText = "实验数据在相同条件下出现相互矛盾的趋势，结论无法由当前结果复核。"
                + "完整原文".repeat(500);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(60))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-I")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("STRENGTH")
                                .severity("LOW")
                                .statement("Block B2 存在严重的数据矛盾，导致结论无法复核。")
                                .blockId("B1")
                                .physicalPage(3)
                                .build()
                ))
                .build();
        PaperDocumentV2 document = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        3, longText.length(), "测试论文", "ZH", "PAPER_PARSE_V2",
                        "2026-09-16T00:00:00Z"
                ),
                null,
                List.of(
                        new PaperDocumentV2.ContentBlockV2(
                                "B1", PaperDocumentV2.BlockType.HEADING, 3, "模型建立",
                                new PaperDocumentV2.HeadingPayload(2, "3", "模型建立"),
                                null, null, null, null, List.of()
                        ),
                        new PaperDocumentV2.ContentBlockV2(
                                "B2", PaperDocumentV2.BlockType.PARAGRAPH, 3, longText,
                                null, null, null, null, null, List.of()
                        )
                ),
                List.of(new PaperDocumentV2.SectionIndex("SEC-1", "模型建立", 2, "B1", 3)),
                null
        );

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                document,
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getFindingType()).isEqualTo("ISSUE");
        assertThat(finding.getPriority()).isEqualTo("P2");
        assertThat(finding.getTitle()).doesNotContain("…", "...");
        assertThat(finding.getAnchorBlockIds()).containsExactly("B2");
        assertThat(finding.getEvidenceQuotes().get(0).getQuoteMarkdown())
                .contains(longText)
                .doesNotContain("原文节选", "...");
        assertThat(finding.getEvidenceQuotes().get(0).getTruncated()).isFalse();
    }

    @Test
    void promotesClearlyPositiveIssueToStrength() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(90))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-POSITIVE")
                                .dimensionCode("DIM_ASSUMPTION_UNDERSTANDING")
                                .type("ISSUE")
                                .severity("LOW")
                                .statement("基本假设论证充分，符号系统完备且量纲严谨。")
                                .blockId("B1")
                                .physicalPage(3)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                document(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getFindingType()).isEqualTo("STRENGTH");
        assertThat(finding.getPriority()).isNull();
        assertThat(finding.getImportance()).isEqualTo("IMPORTANT");
        assertThat(finding.getTitle()).startsWith("亮点：");
    }

    @Test
    void promotesPositiveIssueWhenItsReasonDescribesAnOtherwiseUnsolvableInput() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(90))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-POSITIVE-CONTEXT")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("ISSUE")
                                .severity("LOW")
                                .statement("""
                                        **结论**：楼梯踏面磨损体积的离散几何建模方案合理且具备较强的实操性。

                                        **原因**：针对实测离散点云无法直接解析积分的特点，作者采用三角剖分进行数值积分。
                                        """)
                                .blockId("B1")
                                .physicalPage(3)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                document(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getFindingType()).isEqualTo("STRENGTH");
        assertThat(finding.getTitle()).startsWith("亮点：");
    }

    @Test
    void keepsMixedPositiveAndNegativeIssueAsIssue() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-MIXED")
                                .dimensionCode("DIM_ASSUMPTION_UNDERSTANDING")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("假设说明较完整，但关键适用边界未说明。")
                                .blockId("B1")
                                .physicalPage(3)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                document(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getFindingType()).isEqualTo("ISSUE");
        assertThat(finding.getPriority()).isEqualTo("P2");
        assertThat(finding.getTitle()).startsWith("问题：");
    }

    @Test
    void treatsMissingParameterStrengthWithDeductionAsIssueAndAddsDisclosureEvidence() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-PARAMETER")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("STRENGTH")
                                .severity("MEDIUM")
                                .statement("""
                                        **结论**：计算反推日人流 $n$ 时关键材质参数的数值与单位完全缺省。

                                        **原因**：正文仅说明 $H$ 和 $K$ 参考相关文献，未列出各材料的具体取值。

                                        **影响**：表中结果无法完整复现。
                                        """)
                                .scoreImpact("-1.5")
                                .blockId("B139")
                                .physicalPage(11)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getFindingType()).isEqualTo("ISSUE");
        assertThat(finding.getTitle()).startsWith("问题：").contains("参数");
        assertThat(finding.getAnchorBlockIds()).containsExactly("B138", "B139");
        assertThat(finding.getWhyItMattersMarkdown())
                .contains("具体取值", "单位", "材料映射", "重算样本结果")
                .doesNotContain("上述“", "逐项核对");
    }

    @Test
    void keepsFormulaAndExplanationEvidenceForDimensionalClaimOutsideNotationSection() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-DIMENSION")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("ISSUE")
                                .severity("HIGH")
                                .statement("""
                                        **结论**：磨料磨损公式存在量纲错误与载荷参数缺失。

                                        **原因**：公式 B130 遗漏载荷 $P$，段落 B131 又将无量纲量与 $KP$ 混用。

                                        **影响**：推导链条无法保持量纲齐次。
                                        """)
                                .scoreImpact("-2.0")
                                .blockId("B130")
                                .physicalPage(10)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                formulaDimensionDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getDimensionCode()).isEqualTo("DIM_MATHEMATICAL_MODELING");
        assertThat(finding.getAnchorBlockIds()).containsExactly("B130", "B131");
        assertThat(finding.getEvidenceQuotes())
                .extracting(DeepEvidenceReviewV4Output.EvidenceQuote::getBlockId)
                .containsExactly("B130", "B131");
    }

    @Test
    void dropsFindingWhenTopicProjectionRemovesTheEntireConclusion() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(90))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-EMPTY")
                                .dimensionCode("DIM_ASSUMPTION_UNDERSTANDING")
                                .type("STRENGTH")
                                .severity("LOW")
                                .statement("""
                                        **结论**：摘要与正文的研究框架高度一致。

                                        **原因**：符号表与化学风化指数表给出了变量定义。

                                        **影响**：建模思路清晰连贯。
                                        """)
                                .blockId("B48")
                                .physicalPage(5)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings()).isEmpty();
        assertThat(output.getOverallAssessmentMarkdown()).doesNotContain("亮点：");
    }

    @Test
    void deduplicatesEquivalentAbstractQuantitativeResultIssues() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(
                        finding(
                                "F-ABSTRACT-1",
                                "DIM_RESULT_VALIDATION",
                                "ISSUE",
                                "摘要全文缺乏具体的量化指标与核心数值结论支撑。",
                                "B3"
                        ),
                        finding(
                                "F-ABSTRACT-2",
                                "DIM_ALGORITHM_SOLUTION",
                                "ISSUE",
                                "摘要仅给出定性流程，缺少关键计算结果的定量数值。",
                                "B3"
                        )
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings()).hasSize(1);
        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getAnchorBlockIds()).containsExactly("B3");
        assertThat(finding.getExplanationMarkdown())
                .contains("**原因**", "关键反演结果", "误差范围", "灵敏度响应数值");
        assertThat(finding.getWhyItMattersMarkdown())
                .contains("量化结论", "精度", "稳定性", "实际解释范围");
    }

    @Test
    void resolvesSensitivityClaimsToTheActualSectionAndRejectsFalseEvaluationAbsence() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(75))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-SENSITIVITY")
                                .dimensionCode("DIM_RESULT_VALIDATION")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("""
                                        **结论**：论文正文未提供模型灵敏度分析的具体实验过程与定量结果。

                                        **原因**：虽然目录列出了灵敏度分析，但提供的正文片段仅覆盖至第 2.1 节。

                                        **影响**：为评估模型参数敏感性提供了标准化的度量基准。
                                        """)
                                .scoreImpact("-1.5")
                                .blockId("B3")
                                .physicalPage(1)
                                .build(),
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-EVALUATION")
                                .dimensionCode("DIM_RESULT_VALIDATION")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("模型优缺点评价内容在正文中缺失。")
                                .scoreImpact("-1.0")
                                .blockId("B18")
                                .physicalPage(2)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings())
                .extracting(DeepEvidenceReviewV4Output.Finding::getFindingId)
                .containsExactly("F-SENSITIVITY");
        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getAnchorBlockIds()).containsExactly("B344", "B345", "B346", "B347");
        assertThat(finding.getExplanationMarkdown())
                .contains("参数扰动幅度", "数值响应")
                .doesNotContain("正文片段", "第 2.1 节", "B3", "B18");
        assertThat(finding.getWhyItMattersMarkdown())
                .contains("无法核验敏感性等级", "参数变化下是否稳定")
                .doesNotContain("标准化的度量基准");
    }

    @Test
    void narrowsMixedCodeAndLayoutStrengthToCodeEvidence() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(88))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-CODE")
                                .dimensionCode("DIM_ALGORITHM_SOLUTION")
                                .type("STRENGTH")
                                .severity("LOW")
                                .statement("""
                                        **结论**：附录代码储备与排版综合质量表现良好。

                                        **原因**：排版得分达到 87.3，版面质量为 EXCELLENT，且附录提供了 Python 与 Bash 代码支持计算。

                                        **影响**：为磨损计算提供了算法层面的可复现依据。
                                        """)
                                .blockId("B4")
                                .physicalPage(1)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getTitle()).isEqualTo("亮点：附录代码储备表现良好");
        assertThat(finding.getExplanationMarkdown())
                .contains("Python", "Bash")
                .doesNotContain("排版", "87.3", "EXCELLENT");
        assertThat(finding.getAnchorBlockIds()).containsExactly("B384", "B396");
        assertThat(finding.getEvidenceQuotes())
                .extracting(DeepEvidenceReviewV4Output.EvidenceQuote::getBlockType)
                .containsOnly("CODE");
    }

    @Test
    void supportsTableReferenceMismatchWithBothTheReferenceAndCaption() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(85))
                .dimensions(List.of())
                .findings(List.of(finding(
                        "F-TABLE",
                        "DIM_STRUCTURE_WRITING",
                        "ISSUE",
                        "符号表的正文引用与表题编号不一致。",
                        "B48"
                )))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings().get(0).getAnchorBlockIds())
                .containsExactly("B45", "B48");
    }

    @Test
    void supportsCrossTableNumericContradictionWithMaterialAndResultTables() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(75))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-NUMERIC")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("ISSUE")
                                .severity("HIGH")
                                .statement("""
                                        **结论**：表 3 中各样本计算结果与基本正比例规律存在内部数值矛盾。

                                        **原因**：样本 5 和 6 属于相同采样点与相同材质，
                                        但磨损体积更大的样本反而得到更小的日人流量。

                                        **影响**：结果可能存在参数配置混淆或代入计算错误。
                                        """)
                                .scoreImpact("-2.0")
                                .blockId("B139")
                                .physicalPage(11)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                evidenceGuardDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings().get(0).getAnchorBlockIds())
                .containsExactly("B95", "B139");
    }

    @Test
    void resolvesChineseAdjacentBlockRangesAndRestoresMarkdownLineBreaks() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(78))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-RANGE")
                                .dimensionCode("DIM_MATHEMATICAL_MODELING")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("**结论**：公式推导存在变量定义缺口。\\n\\n"
                                        + "**原因**：Block B3-B4中给出的变量关系没有闭合。")
                                .blockId("B3")
                                .physicalPage(1)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                realShapeDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getAnchorBlockIds()).containsExactly("B3", "B4");
        assertThat(finding.getExplanationMarkdown())
                .contains("\n\n**原因**")
                .doesNotContain("\\n");
    }

    @Test
    void keepsOnlyTheNotationClaimWhenModelMixesIndependentTopics() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        String mixedStatement = """
                **结论**：符号表物理量纲存在不规范，且图表编号存在断档与重复；

                **原因**：符号表中初始风化速率 $W_0$ 量纲为 $\\text{year}^{-1}$，而风化速率常数 $\\lambda$ 的量纲被定义为无量纲“/”，在动力学衰减模型中量纲存在潜在不一致；同时正文中连续出现两次“图 1”（“图 1 我们的工作流程”与“图 1 我们的工作”），且直接出现“Table 2”而前文缺少 Table 1；

                **影响**：损害了数学符号体系的严谨性，并在论文编排上反映出校对疏漏。
                """;
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(84.5))
                .dimensions(List.of())
                .findings(List.of(finding(
                        "F_ASN_002",
                        "DIM_ASSUMPTION_UNDERSTANDING",
                        "ISSUE",
                        mixedStatement,
                        "B48"
                )))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                documentWithDistinctDuplicateFigures(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getAnchorBlockIds()).containsExactly("B48");
        assertThat(finding.getTitle()).contains("符号表物理量纲").doesNotContain("编号");
        assertThat(finding.getExplanationMarkdown())
                .contains("$W_0$", "$\\lambda$", "量纲")
                .doesNotContain("图 1", "Table 1", "Table 2", "编号");
        assertThat(finding.getWhyItMattersMarkdown())
                .contains("数学符号体系")
                .doesNotContain("论文编排", "校对疏漏");
    }

    @Test
    void removesInternalDiagnosticsFromRequirementCoverage() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of())
                .requirementCoverage(List.of(
                        DeepEvidenceReviewV3Output.V3RequirementCoverage.builder()
                                .requirementId("REQ_Q1")
                                .questionNo(1)
                                .questionTitle("问题 1")
                                .status("PARTIAL")
                                .explanation("原因: Cannot construct instance of `com.leetmodel.Dto` "
                                        + "through reference chain")
                                .evidenceBlockIds(List.of())
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                realShapeDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getRequirementCoverage()).singleElement()
                .extracting(DeepEvidenceReviewV3Output.V3RequirementCoverage::getExplanation)
                .asString()
                .contains("自动评审未能完整完成", "重新评审")
                .doesNotContain("Cannot construct", "com.leetmodel", "reference chain");
    }

    @Test
    void doesNotFallbackToUnrelatedFirstBlockOnTheSamePage() {
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
                                .statement("贝叶斯后验中的先验参数没有定义。")
                                .blockId("NOT-EXIST")
                                .physicalPage(3)
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

    @Test
    void rejectsExplicitHeadingReferenceThatCannotSupportContentFinding() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(60))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-I")
                                .dimensionCode("DIM_RESULT_VALIDATION")
                                .type("ISSUE")
                                .severity("HIGH")
                                .statement("Block B1 的灵敏度检验没有报告参数扰动结果。")
                                .blockId("B1")
                                .physicalPage(3)
                                .build()
                ))
                .build();
        PaperDocumentV2 document = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        3, 20, "测试论文", "ZH", "PAPER_PARSE_V2",
                        "2026-09-16T00:00:00Z"
                ),
                null,
                List.of(new PaperDocumentV2.ContentBlockV2(
                        "B1", PaperDocumentV2.BlockType.HEADING, 3, "1 Introduction",
                        new PaperDocumentV2.HeadingPayload(1, "1", "Introduction"),
                        null, null, null, null, List.of()
                )),
                List.of(new PaperDocumentV2.SectionIndex(
                        "SEC-1", "Introduction", 1, "B1", 3
                )),
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assembler.assemble(
                        source,
                        document,
                        new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少可验证论文证据");
    }

    @Test
    void selectsCompleteClaimSpecificEvidenceWithoutFillerBlocks() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(84.5))
                .dimensions(List.of())
                .findings(List.of(
                        finding(
                                "F-ABSTRACT",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "摘要完全缺失关键指标的具体数值和定量计算结果。",
                                "B5"
                        ),
                        finding(
                                "F-UNIT",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "部分物理参数在符号表中的单位和量纲定义不够精确。",
                                "B48"
                        ),
                        finding(
                                "F-FIGURE",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "正文连续出现“Figure 1”图号重复，造成图题对应混淆。",
                                "B48"
                        ),
                        finding(
                                "F-ANALYSIS",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "问题分析仅清单式罗列全部任务，缺少内在关系说明。",
                                "B13"
                        ),
                        finding(
                                "F-ASSUMPTION",
                                "DIM_STRUCTURE_WRITING",
                                "STRENGTH",
                                "论文五项假设均给出了对应的现实依据和解释。",
                                "B50"
                        )
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                documentWithDistinctDuplicateFigures(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(finding(output, "F-ABSTRACT").getAnchorBlockIds())
                .containsExactly("B3", "B4", "B5", "B6", "B7", "B8", "B9", "B10");
        assertThat(finding(output, "F-UNIT").getAnchorBlockIds()).containsExactly("B48");
        assertThat(finding(output, "F-FIGURE").getAnchorBlockIds()).containsExactly("B46", "B75");
        assertThat(finding(output, "F-ANALYSIS").getAnchorBlockIds())
                .containsExactlyElementsOf(blockIds(29, 40));
        assertThat(finding(output, "F-ASSUMPTION").getAnchorBlockIds())
                .containsExactlyElementsOf(blockIds(51, 60));
        assertThat(output.getFindings())
                .flatExtracting(DeepEvidenceReviewV4Output.Finding::getAnchorBlockIds)
                .doesNotContain("B13", "B14", "B45", "B73");
    }

    @Test
    void omitsUnrelatedKnowledgeAndMeaninglessZeroScoreImpact() {
        KnowledgeCitationDTO unrelated = new KnowledgeCitationDTO(
                "KC-1",
                "DOC-1",
                "CHUNK-1",
                "优化模型与算法分类全景",
                "数学建模/模型方法/优化模型与算法分类.md",
                null,
                "sha256",
                "L4",
                "GENERAL_MODELING",
                0.9,
                "优化问题分类与算法选型。"
        );
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(request -> Result.ok(
                new KnowledgeRetrievalResultDTO(
                        "RUN-1",
                        "AI_DIRECTORY_V1",
                        "V1",
                        "INDEX-1",
                        "MANIFEST-1",
                        "SOURCE-1",
                        "SUCCESS",
                        List.of(unrelated)
                )
        ));
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(100))
                .dimensions(List.of(
                        DeepEvidenceReviewV3Output.V3ScoringDimension.builder()
                                .dimensionCode("DIM_STRUCTURE_WRITING")
                                .dimensionName("结构规范与排版可读性")
                                .score(BigDecimal.valueOf(20))
                                .maxScore(BigDecimal.valueOf(20))
                                .build()
                ))
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-FIGURE")
                                .dimensionCode("DIM_STRUCTURE_WRITING")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("正文连续出现“Figure 1”图号重复，造成图题对应混淆。")
                                .scoreImpact("0.0 分")
                                .blockId("B46")
                                .physicalPage(4)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                documentWithDistinctDuplicateFigures(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getScoreImpact()).isNull();
        assertThat(finding.getExplanationMarkdown()).doesNotContain("评分影响", "0.0 分");
        assertThat(finding.getKnowledgeBasisIds()).isEmpty();
        assertThat(output.getKnowledgeBasis()).isEmpty();
        assertThat(output.getDimensions().get(0).getIssueFindingIds()).containsExactly("F-FIGURE");
        assertThat(output.getDimensions().get(0).getReasonMarkdown())
                .contains("补充观察（无独立扣分值）", "图 1 被用于两幅不同插图")
                .doesNotContain("主要限制");
    }

    @Test
    void alignsFigureNumberingIssueWithWritingPriorityAndDirectImpact() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(90))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-FIGURE-ALIGN")
                                .dimensionCode("DIM_ASSUMPTION_UNDERSTANDING")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("图题标号出现重复命名，影响排版与引用的严谨性。")
                                .blockId("B46")
                                .physicalPage(4)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                documentWithDistinctDuplicateFigures(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getDimensionCode()).isEqualTo("DIM_STRUCTURE_WRITING");
        assertThat(finding.getPriority()).isEqualTo("P3");
        assertThat(finding.getAnchorBlockIds()).containsExactly("B46", "B75");
        assertThat(finding.getExplanationMarkdown())
                .contains("图 1 被用于两幅不同插图")
                .contains("第 4 页", "Figure 1: Our workflow")
                .contains("第 7 页", "Figure 1: Validation overview");
        assertThat(finding.getWhyItMattersMarkdown())
                .contains("正文引用", "图题", "一一对应")
                .doesNotContain("模型前提", "适用边界");
    }

    @Test
    void rejectsDuplicateFigureClaimWhenBlocksDescribeTheSameFigure() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(90))
                .dimensions(List.of())
                .findings(List.of(
                        finding(
                                "F-FIGURE-FALSE-POSITIVE",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "Figure 1 图号重复，造成图题对应混淆。",
                                "B46"
                        ),
                        finding(
                                "F-ABSTRACT-VALID",
                                "DIM_RESULT_VALIDATION",
                                "ISSUE",
                                "摘要缺少关键结果的具体数值。",
                                "B5"
                        )
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                realShapeDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings())
                .extracting(DeepEvidenceReviewV4Output.Finding::getFindingId)
                .containsExactly("F-ABSTRACT-VALID");
    }

    @Test
    void deduplicatesEquivalentDuplicateFigureFindings() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(90))
                .dimensions(List.of())
                .findings(List.of(
                        finding(
                                "F-FIGURE-ONE",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "Figure 1 图号重复，造成正文引用混淆。",
                                "B46"
                        ),
                        finding(
                                "F-FIGURE-TWO",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "论文中两幅不同插图的 Figure 1 图号重复。",
                                "B75"
                        )
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                documentWithDistinctDuplicateFigures(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings()).hasSize(1);
        assertThat(output.getFindings().get(0).getFindingId()).isEqualTo("F-FIGURE-ONE");
        assertThat(output.getFindings().get(0).getAnchorBlockIds()).containsExactly("B46", "B75");
    }

    @Test
    void completesProblemAnalysisIssueWithEvidenceSpecificReasonAndImpact() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(85))
                .dimensions(List.of())
                .findings(List.of(
                        DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId("F-PROBLEM-ALIGN")
                                .dimensionCode("DIM_ASSUMPTION_UNDERSTANDING")
                                .type("ISSUE")
                                .severity("MEDIUM")
                                .statement("问题分析环节对多个子任务间的因果关联与数据流传导剖析不够深入。")
                                .blockId("B29")
                                .physicalPage(3)
                                .build()
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                realShapeDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        DeepEvidenceReviewV4Output.Finding finding = output.getFindings().get(0);
        assertThat(finding.getPriority()).isEqualTo("P3");
        assertThat(finding.getAnchorBlockIds()).containsExactlyElementsOf(blockIds(29, 40));
        assertThat(finding.getExplanationMarkdown())
                .contains("逐项复述任务", "前序测量量", "相互校验");
        assertThat(finding.getWhyItMattersMarkdown())
                .contains("数据流", "建模主线", "衔接依据");
    }

    @Test
    void keepsOnlyStronglyMatchedKnowledgeAndRemovesExternalImages() {
        KnowledgeCitationDTO related = new KnowledgeCitationDTO(
                "KC-UNIT",
                "DOC-UNIT",
                "CHUNK-UNIT",
                "符号与量纲规范",
                "数学建模/论文写作/符号与量纲规范.md",
                "单位一致性",
                "sha256",
                "L3",
                "NOTATION",
                0.9,
                "变量单位应与方程保持一致。\n"
                        + "![外部图](https://example.com/unit.png)\n"
                        + "<img src=\"https://example.com/unit-2.png\" alt=\"unit\">"
        );
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(request -> Result.ok(
                new KnowledgeRetrievalResultDTO(
                        "RUN-1",
                        "AI_DIRECTORY_V1",
                        "V1",
                        "INDEX-1",
                        "MANIFEST-1",
                        "SOURCE-1",
                        "SUCCESS",
                        List.of(related)
                )
        ));
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(finding(
                        "F-UNIT",
                        "DIM_ASSUMPTION_UNDERSTANDING",
                        "ISSUE",
                        "部分物理参数在符号表中的单位和量纲定义不够精确。",
                        "B48"
                )))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                realShapeDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getKnowledgeBasis()).hasSize(1);
        assertThat(output.getFindings().get(0).getKnowledgeBasisIds()).containsExactly("KB-1");
        assertThat(output.getKnowledgeBasis().get(0).getSupportMarkdown())
                .contains("变量单位应与方程保持一致")
                .doesNotContain("https://", "<img", "![");
    }

    @Test
    void omitsBroadAlgorithmKnowledgeForGenericModelAndCodeClaims() {
        KnowledgeCitationDTO broadAlgorithm = new KnowledgeCitationDTO(
                "KC-ALGORITHM",
                "DOC-ALGORITHM",
                "CHUNK-ALGORITHM",
                "优化模型与算法分类",
                "数学建模/模型方法/优化模型与算法分类.md",
                null,
                "sha256",
                "L4",
                "GENERAL_MODELING",
                0.9,
                "介绍通用优化算法分类。"
        );
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(request -> Result.ok(
                new KnowledgeRetrievalResultDTO(
                        "RUN-1",
                        "AI_DIRECTORY_V1",
                        "V1",
                        "INDEX-1",
                        "MANIFEST-1",
                        "SOURCE-1",
                        "SUCCESS",
                        List.of(broadAlgorithm)
                )
        ));
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(
                        finding(
                                "F-GENERIC",
                                "DIM_ALGORITHM_SOLUTION",
                                "ISSUE",
                                "贝叶斯反演流程存在参数循环依赖。",
                                "B130"
                        ),
                        finding(
                                "F-CODE",
                                "DIM_ALGORITHM_SOLUTION",
                                "STRENGTH",
                                "附录代码支持计算流程复现。",
                                "B384"
                        )
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                knowledgeSelectionDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings())
                .allSatisfy(item -> assertThat(item.getKnowledgeBasisIds()).isEmpty());
        assertThat(output.getKnowledgeBasis()).isEmpty();
    }

    @Test
    void explainsWhyIncompleteAppendixReproductionMatters() {
        ReviewV4ReportAssembler assembler = new ReviewV4ReportAssembler(null);
        DeepEvidenceReviewV3Output source = DeepEvidenceReviewV3Output.builder()
                .score(BigDecimal.valueOf(80))
                .dimensions(List.of())
                .findings(List.of(
                        finding(
                                "F-CODE-WEAK",
                                "DIM_STRUCTURE_WRITING",
                                "ISSUE",
                                "附录代码规模较小且复现文档支撑不足。",
                                "B384"
                        )
                ))
                .build();

        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                knowledgeSelectionDocument(),
                new ProblemContextDTO(1L, "测试题", "题面", 180, 1)
        );

        assertThat(output.getFindings().get(0).getWhyItMattersMarkdown())
                .contains("执行路径、依赖环境和关键参数配置", "完整复现")
                .doesNotContain("会直接影响评阅者对论文结构");
    }

    private DeepEvidenceReviewV3Output.V3Finding finding(
            String id,
            String dimensionCode,
            String type,
            String statement,
            String blockId
    ) {
        return DeepEvidenceReviewV3Output.V3Finding.builder()
                .findingId(id)
                .dimensionCode(dimensionCode)
                .type(type)
                .severity("MEDIUM")
                .statement(statement)
                .scoreImpact("0.0 分")
                .blockId(blockId)
                .physicalPage(1)
                .build();
    }

    private DeepEvidenceReviewV4Output.Finding finding(
            DeepEvidenceReviewV4Output output,
            String id
    ) {
        return output.getFindings().stream()
                .filter(item -> id.equals(item.getFindingId()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> blockIds(int start, int end) {
        List<String> result = new ArrayList<>();
        for (int number = start; number <= end; number++) {
            result.add("B" + number);
        }
        return result;
    }

    private PaperDocumentV2 realShapeDocument() {
        List<PaperDocumentV2.ContentBlockV2> blocks = new ArrayList<>();
        blocks.add(heading("B2", 1, "Summary"));
        for (int number = 3; number <= 10; number++) {
            blocks.add(paragraph("B" + number, 1, "Abstract paragraph " + number + " without metrics."));
        }
        blocks.add(heading("B12", 2, "Contents"));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B13",
                PaperDocumentV2.BlockType.LIST_ITEM,
                2,
                "1 Introduction ... 3",
                null,
                null,
                null,
                null,
                null,
                List.of()
        ));
        blocks.add(heading("B28", 3, "Problem Restatement and Analysis"));
        for (int number = 29; number <= 40; number++) {
            blocks.add(paragraph("B" + number, 3, "Problem " + (number - 28) + " restatement."));
        }
        blocks.add(heading("B44", 4, "Notations"));
        blocks.add(paragraph("B45", 4, "The primary notations are listed in Table 1."));
        blocks.add(figure("B46", 4, "Figure 1: Our workflow"));
        blocks.add(figure("B47", 4, "Figure 1: Our work"));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B48",
                PaperDocumentV2.BlockType.TABLE,
                5,
                "Table 1: Notations",
                null,
                null,
                new PaperDocumentV2.TablePayload(
                        "Table 1: Notations",
                        "ABOVE",
                        "1",
                        "<table><tr><th>Symbol</th><th>Unit</th></tr>"
                                + "<tr><td>lambda</td><td>/</td></tr></table>",
                        null
                ),
                null,
                null,
                List.of()
        ));
        blocks.add(heading("B49", 5, "Assumptions"));
        blocks.add(paragraph("B50", 5, "We make the following reasonable assumptions."));
        for (int number = 1; number <= 5; number++) {
            int blockNumber = 49 + number * 2;
            blocks.add(paragraph(
                    "B" + blockNumber,
                    5,
                    "Assumption " + number + ": assumption statement."
            ));
            blocks.add(paragraph(
                    "B" + (blockNumber + 1),
                    5,
                    "Explanation for assumption " + number + "."
            ));
        }
        blocks.add(heading("B61", 6, "Detailed Measurement Plan"));
        blocks.add(figure("B73", 6, "Figure 2: Stairs model"));

        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        6,
                        1000,
                        "测试论文",
                        "EN",
                        "PAPER_PARSE_V2",
                        "2026-09-16T00:00:00Z"
                ),
                null,
                blocks,
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC-2", "Summary", 2, "B2", 1),
                        new PaperDocumentV2.SectionIndex("SEC-3", "Contents", 1, "B12", 2),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-7",
                                "Problem Restatement and Analysis",
                                2,
                                "B28",
                                3
                        ),
                        new PaperDocumentV2.SectionIndex("SEC-10", "Notations", 2, "B44", 4),
                        new PaperDocumentV2.SectionIndex("SEC-11", "Assumptions", 2, "B49", 5),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-12",
                                "Detailed Measurement Plan",
                                2,
                                "B61",
                                6
                        )
                ),
                null
        );
    }

    private PaperDocumentV2 documentWithDistinctDuplicateFigures() {
        PaperDocumentV2 source = realShapeDocument();
        List<PaperDocumentV2.ContentBlockV2> blocks = new ArrayList<>(source.blocks());
        blocks.add(structuredFigure(
                "B75",
                7,
                "Figure 1: Validation overview",
                "Independent validation result chart"
        ));
        return new PaperDocumentV2(
                source.schemaVersion(),
                source.submissionId(),
                source.contentSha256(),
                source.metadata(),
                source.layoutAesthetics(),
                blocks,
                source.sections(),
                source.quality()
        );
    }

    private PaperDocumentV2 evidenceGuardDocument() {
        List<PaperDocumentV2.ContentBlockV2> blocks = new ArrayList<>();
        blocks.add(heading("B2", 1, "Summary"));
        blocks.add(paragraph("B3", 1, "The paper presents the modeling process without numerical results."));
        blocks.add(heading("B12", 2, "Contents"));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B18",
                PaperDocumentV2.BlockType.LIST_ITEM,
                2,
                "6.1 Sensitivity; 6.3 Strength and Weakness",
                null,
                null,
                null,
                null,
                null,
                List.of()
        ));
        blocks.add(heading("B44", 4, "Notations"));
        blocks.add(paragraph("B45", 4, "The primary notations are listed in Table 2."));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B48",
                PaperDocumentV2.BlockType.TABLE,
                5,
                "Table 1: Notations",
                null,
                null,
                new PaperDocumentV2.TablePayload(
                        "Table 1: Notations",
                        "ABOVE",
                        "1",
                        "<table><tr><th>Symbol</th><th>Unit</th></tr>"
                                + "<tr><td>H</td><td>MPa</td></tr>"
                                + "<tr><td>K</td><td>/</td></tr></table>",
                        null
                ),
                null,
                null,
                List.of()
        ));
        blocks.add(heading("B93", 8, "Data collection and processing"));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B95",
                PaperDocumentV2.BlockType.TABLE,
                8,
                "Table 2: Summary Table of Stair Sample Data",
                null,
                null,
                new PaperDocumentV2.TablePayload(
                        "Table 2: Summary Table of Stair Sample Data",
                        "ABOVE",
                        "2",
                        "<table><tr><th>Stair ID</th><th>Sampling Point</th><th>Material</th></tr>"
                                + "<tr><td>5</td><td>2</td><td>Blue Slate</td></tr>"
                                + "<tr><td>6</td><td>2</td><td>Blue Slate</td></tr></table>",
                        null
                ),
                null,
                null,
                List.of()
        ));
        blocks.add(paragraph(
                "B138",
                11,
                "The values of H and K used in the estimation were referenced from relevant literature."
        ));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B139",
                PaperDocumentV2.BlockType.TABLE,
                11,
                "Table 3: Foot Traffic Estimation",
                null,
                null,
                new PaperDocumentV2.TablePayload(
                        "Table 3: Foot Traffic Estimation",
                        "ABOVE",
                        "3",
                        "<table><tr><th>V</th><th>n</th></tr><tr><td>100</td><td>20</td></tr></table>",
                        null
                ),
                null,
                null,
                List.of()
        ));
        blocks.add(heading("B343", 23, "Sensitivity of Model I"));
        blocks.add(paragraph("B344", 23, "Sensitivity is calculated using the following equation."));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B345",
                PaperDocumentV2.BlockType.FORMULA,
                23,
                "Sensitivity equation",
                null,
                new PaperDocumentV2.FormulaPayload(
                        "\\text{Sensitivity}=\\frac{\\Delta n/n}{\\Delta\\theta/\\theta}",
                        "(42)",
                        false
                ),
                null,
                null,
                null,
                List.of()
        ));
        blocks.add(paragraph("B346", 23, "Delta n is output change and Delta theta is input change."));
        blocks.add(paragraph(
                "B347",
                23,
                "Load is highly sensitive, while hardness and wear coefficient have medium sensitivity."
        ));
        blocks.add(heading("B352", 24, "Strength and Weakness"));
        blocks.add(heading("B353", 24, "Strength"));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B354",
                PaperDocumentV2.BlockType.LIST_ITEM,
                24,
                "The model combines multiple data sources.",
                null,
                null,
                null,
                null,
                null,
                List.of()
        ));
        blocks.add(heading("B356", 24, "Weakness"));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B357",
                PaperDocumentV2.BlockType.LIST_ITEM,
                24,
                "Measurement accuracy and random walking trajectories limit the model.",
                null,
                null,
                null,
                null,
                null,
                List.of()
        ));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B384",
                PaperDocumentV2.BlockType.CODE,
                26,
                "Python appendix code",
                null,
                null,
                null,
                null,
                new PaperDocumentV2.CodePayload("python", "import numpy as np\nprint(np.arange(3))"),
                List.of()
        ));
        blocks.add(new PaperDocumentV2.ContentBlockV2(
                "B396",
                PaperDocumentV2.BlockType.CODE,
                28,
                "Bash dependency command",
                null,
                null,
                null,
                null,
                new PaperDocumentV2.CodePayload("bash", "pip install numpy"),
                List.of()
        ));

        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        31,
                        5000,
                        "测试论文",
                        "EN",
                        "PAPER_PARSE_V2",
                        "2026-09-16T00:00:00Z"
                ),
                new PaperDocumentV2.LayoutAesthetics(
                        87.3,
                        "MEDIUM",
                        "EXCELLENT",
                        "layout"
                ),
                blocks,
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC-2", "Summary", 2, "B2", 1),
                        new PaperDocumentV2.SectionIndex("SEC-3", "Contents", 1, "B12", 2),
                        new PaperDocumentV2.SectionIndex("SEC-10", "Notations", 2, "B44", 4),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-18",
                                "Data collection and processing",
                                2,
                                "B93",
                                8
                        ),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-64",
                                "Sensitivity of Model I",
                                2,
                                "B343",
                                23
                        ),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-66",
                                "Strength and Weakness",
                                2,
                                "B352",
                                24
                        ),
                        new PaperDocumentV2.SectionIndex("SEC-67", "Strength", 3, "B353", 24),
                        new PaperDocumentV2.SectionIndex("SEC-68", "Weakness", 3, "B356", 24),
                        new PaperDocumentV2.SectionIndex("SEC-70", "Report on Use of AI", 1, "B384", 26)
                ),
                null
        );
    }

    private PaperDocumentV2 formulaDimensionDocument() {
        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        10,
                        200,
                        "测试论文",
                        "EN",
                        "PAPER_PARSE_V2",
                        "2026-09-17T00:00:00Z"
                ),
                null,
                List.of(
                        heading("B129", 10, "Archard Abrasive Wear Model"),
                        new PaperDocumentV2.ContentBlockV2(
                                "B130",
                                PaperDocumentV2.BlockType.FORMULA,
                                10,
                                "$$V = K_{abr} \\cdot \\frac{L}{3H}$$",
                                null,
                                new PaperDocumentV2.FormulaPayload(
                                        "V = K_{abr} \\cdot \\frac{L}{3H}",
                                        "(6)",
                                        false
                                ),
                                null,
                                null,
                                null,
                                List.of()
                        ),
                        paragraph(
                                "B131",
                                10,
                                "The values of tan theta and KP show little difference."
                        )
                ),
                List.of(new PaperDocumentV2.SectionIndex(
                        "SEC-130",
                        "Archard Abrasive Wear Model",
                        2,
                        "B129",
                        10
                )),
                null
        );
    }

    private PaperDocumentV2 knowledgeSelectionDocument() {
        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1L,
                "sha256",
                new PaperDocumentV2.DocumentMetadata(
                        26,
                        300,
                        "测试论文",
                        "EN",
                        "PAPER_PARSE_V2",
                        "2026-09-17T00:00:00Z"
                ),
                null,
                List.of(
                        paragraph("B130", 10, "Bayesian inversion depends on coupled parameters."),
                        new PaperDocumentV2.ContentBlockV2(
                                "B384",
                                PaperDocumentV2.BlockType.CODE,
                                26,
                                "print('review')",
                                null,
                                null,
                                null,
                                null,
                                new PaperDocumentV2.CodePayload(
                                        "python",
                                        "print('review')"
                                ),
                                List.of()
                        )
                ),
                List.of(),
                null
        );
    }

    private PaperDocumentV2.ContentBlockV2 heading(
            String blockId,
            int page,
            String title
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.HEADING,
                page,
                title,
                new PaperDocumentV2.HeadingPayload(2, "", title),
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private PaperDocumentV2.ContentBlockV2 paragraph(
            String blockId,
            int page,
            String text
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.PARAGRAPH,
                page,
                text,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private PaperDocumentV2.ContentBlockV2 figure(
            String blockId,
            int page,
            String caption
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.FIGURE,
                page,
                caption,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private PaperDocumentV2.ContentBlockV2 structuredFigure(
            String blockId,
            int page,
            String caption,
            String description
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.FIGURE,
                page,
                caption,
                null,
                null,
                null,
                new PaperDocumentV2.FigurePayload(
                        caption,
                        "BELOW",
                        "1",
                        "CHART",
                        description,
                        90.0,
                        "清晰",
                        List.of()
                ),
                null,
                List.of()
        );
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
