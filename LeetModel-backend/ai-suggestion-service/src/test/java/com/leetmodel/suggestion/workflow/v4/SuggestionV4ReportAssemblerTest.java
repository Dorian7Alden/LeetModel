package com.leetmodel.suggestion.workflow.v4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.workflow.v3.GroundedSuggestionV3Output;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestionV4ReportAssemblerTest {

    private final SuggestionV4ReportAssembler assembler =
            new SuggestionV4ReportAssembler(new ObjectMapper());

    @Test
    void convertsCorrectionAndAdvancementIntoThreeTypeBoundary() throws Exception {
        GroundedSuggestionV3Output source = new GroundedSuggestionV3Output(
                "GROUNDED_SUGGESTION_V3",
                "旧策略",
                List.of("旧优先项"),
                List.of(),
                List.of(
                        item("S-1", "CORRECTION", "P3", "必须改用其他模型", List.of("F-1")),
                        item("S-2", "ADVANCEMENT", "P2", "建议采用基准模型进行核验", List.of())
                )
        );
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(new ReviewEvidenceSnapshot.Finding(
                        "F-1",
                        "ISSUE",
                        "MODEL",
                        "P1",
                        "量纲不闭合",
                        "量纲不闭合会使公式含义无法复核。",
                        "-2.0",
                        "$.findings[0]",
                        List.of("B1")
                )),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                source,
                parse(),
                evidence,
                knowledge()
        );

        assertThat(output.items()).extracting(GroundedSuggestionV4Output.Item::guidanceType)
                .containsExactly("REQUIRED_FIX", "OPTIONAL_EXPLORATION");
        assertThat(output.items().get(0).priority()).isEqualTo("P1");
        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("当前模型适用性")
                .doesNotContain("必须改用");
        assertThat(output.items().get(0).currentStateMarkdown())
                .contains("量纲不闭合")
                .doesNotContain("当前论文事实");
        assertThat(output.items().get(0).rationaleMarkdown())
                .contains("公式含义无法复核");
        assertThat(output.items().get(1).guidanceMarkdown())
                .contains("可选探索方向")
                .doesNotContain("建议采用");
        assertThat(output.items().get(0).evidenceQuotes().get(0).quoteMarkdown())
                .contains("原模型使用常量参数");
        assertThat(output.items().get(0).targetLocation().physicalPages())
                .containsExactly(5);
        assertThat(output.items().get(0).evidenceChain().knowledgeBasisIds())
                .containsExactly("KB-1");
        assertThat(output.knowledgeBasis()).hasSize(1);
    }

    @Test
    void keepsGroundedCandidateWhenKnowledgeCitationDoesNotMatchSnapshot() throws Exception {
        GroundedSuggestionV3Output source = new GroundedSuggestionV3Output(
                "GROUNDED_SUGGESTION_V3",
                "旧策略",
                List.of(),
                List.of(),
                List.of(item(
                        "S-1",
                        "ADVANCEMENT",
                        "P2",
                        "补充参数关系说明",
                        List.of(),
                        List.of("KC-NOT-IN-SNAPSHOT")
                ))
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                source,
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(),
                        "{}"
                ),
                knowledge()
        );

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).evidenceChain().knowledgeBasisIds()).isEmpty();
        assertThat(output.knowledgeBasis()).isEmpty();
    }

    @Test
    void softensDirectOptionalSectionInstruction() throws Exception {
        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(item(
                                "S-1",
                                "ADVANCEMENT",
                                "P2",
                                "建议在第 6 节补充参数扰动与稳定性说明。",
                                List.of()
                        ))
                ),
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(),
                        "{}"
                ),
                knowledge()
        );

        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("可考虑在第 6 节补充")
                .doesNotContain("建议在第");
    }

    @Test
    void promotesSuggestionBoundToReviewIssueToRequiredFix() throws Exception {
        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(item(
                                "S-1",
                                "ADVANCEMENT",
                                "P3",
                                "### 可选探索方向\n\n"
                                        + "若希望进一步增强现有论证的说服力，"
                                        + "可以从以下方向选择性补充：\n\n"
                                        + "补充附录执行路径与依赖说明。",
                                List.of("F-1")
                        ))
                ),
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(finding("F-1", "附录代码无法完整复现。")),
                        "{}"
                ),
                null
        );

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).guidanceType()).isEqualTo("REQUIRED_FIX");
        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("补充附录执行路径与依赖说明")
                .doesNotContain("可选探索方向", "若希望进一步增强");
    }

    @Test
    void replacesDirectModelingPathWithGuardedIdentifiabilityGuidance() throws Exception {
        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(item(
                                "S-1",
                                "CORRECTION",
                                "P2",
                                "建议从参数可辨识性角度重构反演机制：\n\n补充未知量与约束说明。",
                                List.of("F-1")
                        ))
                ),
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(finding("F-1", "反演模型存在参数不可辨识问题。")),
                        "{}"
                ),
                null
        );

        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("未知量、约束条件、可辨识性和误差传播")
                .doesNotContain("重构反演机制", "补充未知量与约束说明");
        assertThat(output.items().get(0).acceptanceCriteriaMarkdown())
                .singleElement()
                .asString()
                .contains("适用边界");
    }

    @Test
    void replacesSuggestionThatMixesMultipleReviewFindingsWithIndependentFallbacks() throws Exception {
        GroundedSuggestionV3Output.Item mixed = item(
                "S-1",
                "CORRECTION",
                "P1",
                "同时补充量纲与图表编号说明",
                List.of("F-1", "F-2")
        );
        GroundedSuggestionV3Output.Item independent = item(
                "S-2",
                "ADVANCEMENT",
                "P2",
                "补充参数适用边界",
                List.of()
        );
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(
                        finding("F-1", "量纲不闭合"),
                        finding("F-2", "图表编号重复")
                ),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(mixed, independent)
                ),
                parse(),
                evidence,
                knowledge()
        );

        assertThat(output.items()).hasSize(3);
        assertThat(output.items().stream()
                .filter(item -> "REQUIRED_FIX".equals(item.guidanceType()))
                .map(item -> item.evidenceChain().reviewFindingIds().get(0)))
                .containsExactly("F-1", "F-2");
    }

    @Test
    void keepsCompleteLongPaperQuoteWithoutEllipsis() throws Exception {
        String longText = "这是完整论文证据。".repeat(300);
        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(item("S-1", "ADVANCEMENT", "P2", "补充适用边界", List.of()))
                ),
                parse(longText),
                new ReviewEvidenceSnapshot(
                        1L, 1L, "DEEP_EVIDENCE_REVIEW_V4", null, List.of(), "{}"
                ),
                knowledge()
        );

        GroundedSuggestionV4Output.EvidenceQuote quote =
                output.items().get(0).evidenceQuotes().get(0);
        assertThat(quote.quoteMarkdown()).contains(longText).doesNotContain("原文节选", "...");
        assertThat(quote.truncated()).isFalse();
    }

    @Test
    void replacesGuidanceThatIntroducesUnrelatedTopicsWithFindingFallback() throws Exception {
        GroundedSuggestionV3Output.Item mixed = item(
                "S-1",
                "CORRECTION",
                "P1",
                "补充量纲说明，同时修正图题编号和灵敏度检验。",
                List.of("F-1")
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(mixed)
                ),
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(finding("F-1", "当前公式量纲不闭合。")),
                        "{}"
                ),
                knowledge()
        );

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("符号、参数、单位、量纲或公式关系")
                .doesNotContain("图题编号", "灵敏度检验");
    }

    @Test
    void removesRepeatedCurrentStateSectionFromGuidance() throws Exception {
        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(item(
                                "S-1",
                                "CORRECTION",
                                "P1",
                                "### 当前论文事实\n\n原模型量纲不闭合。\n\n"
                                        + "### 建议补充的方面\n\n补充变量单位与量纲核对说明。",
                                List.of("F-1")
                        ))
                ),
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(finding("F-1", "当前公式量纲不闭合。")),
                        "{}"
                ),
                knowledge()
        );

        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("补充变量单位与量纲核对说明")
                .doesNotContain("当前论文事实", "原模型量纲不闭合");
    }

    @Test
    void inheritsCompletePaperEvidenceFromSingleReviewFinding() throws Exception {
        GroundedSuggestionV3Output source = new GroundedSuggestionV3Output(
                "GROUNDED_SUGGESTION_V3",
                "旧策略",
                List.of(),
                List.of(),
                List.of(new GroundedSuggestionV3Output.Item(
                        "S-1",
                        "P1",
                        "CORRECTION",
                        "WRITING",
                        1,
                        "区分重复图号",
                        "正文存在 Figure 1 图号重复。",
                        "重复图号会使图文对应关系无法复核。",
                        new GroundedSuggestionV3Output.TargetLocation(
                                List.of(5),
                                "图表",
                                List.of("B1")
                        ),
                        "补充两个同号图题的编号区分说明。",
                        List.of("两个图题能够通过不同图号被准确引用。"),
                        new GroundedSuggestionV3Output.EvidenceChain(
                                List.of("B1"),
                                List.of("F-1"),
                                List.of()
                        )
                ))
        );
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(new ReviewEvidenceSnapshot.Finding(
                        "F-1",
                        "ISSUE",
                        "WRITING",
                        "P1",
                        "正文存在 Figure 1 图号重复。",
                        "重复图号会使图文对应关系无法复核。",
                        null,
                        "$.findings[0]",
                        List.of("B1", "B2")
                )),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                source,
                parseTwoBlocks(),
                evidence,
                null
        );

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).evidenceChain().paperEvidenceIds())
                .containsExactly("B1", "B2");
        assertThat(output.items().get(0).evidenceQuotes())
                .extracting(GroundedSuggestionV4Output.EvidenceQuote::blockId)
                .containsExactly("B1", "B2");
    }

    @Test
    void removesExternalImagesFromMatchedKnowledgeBasis() throws Exception {
        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(item(
                                "S-1",
                                "ADVANCEMENT",
                                "P2",
                                "补充参数关系说明。",
                                List.of()
                        ))
                ),
                parse(),
                new ReviewEvidenceSnapshot(
                        1L,
                        1L,
                        "DEEP_EVIDENCE_REVIEW_V4",
                        null,
                        List.of(),
                        "{}"
                ),
                knowledgeWithExternalImages()
        );

        assertThat(output.knowledgeBasis()).hasSize(1);
        assertThat(output.knowledgeBasis().get(0).supportMarkdown())
                .contains("参数来源和关系")
                .doesNotContain("https://", "<img", "![");
    }

    @Test
    void fillsMissingReviewIssueWithEvidenceBoundFallback() throws Exception {
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(
                        finding("F-1", "当前公式量纲不闭合。", List.of("B1")),
                        finding("F-2", "正文图号重复。", List.of("B2"))
                ),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(issueItem(
                                "S-1",
                                "F-1",
                                "补充公式量纲核对说明。",
                                "公式两侧量纲能够逐项核对。",
                                "B1"
                        ))
                ),
                parseTwoBlocks(),
                evidence,
                null
        );

        assertThat(output.items()).hasSize(2);
        GroundedSuggestionV4Output.Item fallback = output.items().get(1);
        assertThat(fallback.evidenceChain().reviewFindingIds()).containsExactly("F-2");
        assertThat(fallback.evidenceChain().paperEvidenceIds()).containsExactly("B2");
        assertThat(fallback.evidenceQuotes())
                .extracting(GroundedSuggestionV4Output.EvidenceQuote::blockId)
                .containsExactly("B2");
        assertThat(fallback.guidanceMarkdown()).contains("图号、图题或正文引用");
    }

    @Test
    void keepsOnlyFirstValidSuggestionWhenReviewIssueIsReferencedMoreThanOnce() throws Exception {
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(finding("F-1", "当前公式量纲不闭合。", List.of("B1"))),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(
                                issueItem(
                                        "S-1",
                                        "F-1",
                                        "补充公式量纲核对说明。",
                                        "公式两侧量纲能够逐项核对。",
                                        "B1"
                                ),
                                issueItem(
                                        "S-2",
                                        "F-1",
                                        "补充变量单位对应说明。",
                                        "变量单位与公式量纲能够对应。",
                                        "B1"
                                )
                        )
                ),
                parseTwoBlocks(),
                evidence,
                null
        );

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).guidanceMarkdown())
                .isEqualTo("补充公式量纲核对说明。");
    }

    @Test
    void acceptsOneSuggestionForEachReviewIssueWithCompleteEvidence() throws Exception {
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(
                        finding("F-1", "当前公式量纲不闭合。", List.of("B1")),
                        finding("F-2", "正文图号重复。", List.of("B2"))
                ),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of(
                                issueItem(
                                        "S-1",
                                        "F-1",
                                        "补充公式量纲核对说明。",
                                        "公式两侧量纲能够逐项核对。",
                                        "B1"
                                ),
                                issueItem(
                                        "S-2",
                                        "F-2",
                                        "补充重复图号的区分说明。",
                                        "每幅图都能通过独立图号被准确引用。",
                                        "B2"
                                )
                        )
                ),
                parseTwoBlocks(),
                evidence,
                null
        );

        assertThat(output.items()).hasSize(2);
        assertThat(output.items())
                .extracting(item -> item.evidenceChain().reviewFindingIds().get(0))
                .containsExactly("F-1", "F-2");
        assertThat(output.items().get(0).evidenceChain().paperEvidenceIds())
                .containsExactly("B1");
        assertThat(output.items().get(1).evidenceChain().paperEvidenceIds())
                .containsExactly("B2");
    }

    @Test
    void fillsTwoMissingSlotsInFiveIssueReportWithoutChangingExistingEvidence() throws Exception {
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(
                        finding("find_001", "摘要缺少关键结果说明。", List.of("B1")),
                        finding("find_002", "正文图号重复。", List.of("B2")),
                        finding("find_003", "当前公式量纲不闭合。", List.of("B3")),
                        finding("F_ABS_002", "摘要结论与正文对应不足。", List.of("B4")),
                        finding("F_TYP_001", "符号定义与正文使用不一致。", List.of("B5"))
                ),
                "{}"
        );
        GroundedSuggestionV3Output source = new GroundedSuggestionV3Output(
                "GROUNDED_SUGGESTION_V3",
                "旧策略",
                List.of(),
                List.of(),
                List.of(
                        issueItem("S-1", "find_002", "补充重复图号的区分说明。",
                                "每幅图都能被准确引用。", "B2"),
                        issueItem("S-2", "find_003", "补充公式量纲核对说明。",
                                "公式两侧量纲能够逐项核对。", "B3"),
                        issueItem("S-3", "F_TYP_001", "补充符号定义与使用位置的对应说明。",
                                "符号定义与正文使用保持一致。", "B5")
                )
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                source,
                parseFiveBlocks(),
                evidence,
                null
        );

        assertThat(output.items()).hasSize(5);
        assertThat(output.items())
                .allMatch(item -> "REQUIRED_FIX".equals(item.guidanceType()))
                .allMatch(item -> item.evidenceChain().reviewFindingIds().size() == 1);
        assertThat(output.items())
                .extracting(item -> item.evidenceChain().reviewFindingIds().get(0))
                .containsExactlyInAnyOrder(
                        "find_001",
                        "find_002",
                        "find_003",
                        "F_ABS_002",
                        "F_TYP_001"
                );
        GroundedSuggestionV4Output.Item firstFallback = itemForFinding(output, "find_001");
        GroundedSuggestionV4Output.Item secondFallback = itemForFinding(output, "F_ABS_002");
        assertThat(firstFallback.evidenceChain().paperEvidenceIds())
                .containsExactly("B1");
        assertThat(secondFallback.evidenceChain().paperEvidenceIds())
                .containsExactly("B4");
    }

    @Test
    void fallbackForContradictoryTableResultKeepsTheFindingTopic() throws Exception {
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(new ReviewEvidenceSnapshot.Finding(
                        "F-DATA",
                        "ISSUE",
                        "MODEL",
                        "P1",
                        "表 11 中相同材料与相同使用时间的样本人流量与理论公式呈反向变动。",
                        "样本数据与公式推导结果不一致，使核心结论无法复核。",
                        "-8.0",
                        "$.findings[0]",
                        List.of("B1")
                )),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of()
                ),
                parse(),
                evidence,
                null
        );

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("不一致现象", "数据、样本或计算口径", "理论公式和正文结论")
                .doesNotContain("符号、参数、单位、量纲");
    }

    @Test
    void fallbackForIdentifiabilityFindingExplainsTheClosedModelRelation() throws Exception {
        ReviewEvidenceSnapshot evidence = new ReviewEvidenceSnapshot(
                1L,
                1L,
                "DEEP_EVIDENCE_REVIEW_V4",
                null,
                List.of(new ReviewEvidenceSnapshot.Finding(
                        "F-INVERSE",
                        "ISSUE",
                        "SOLUTION",
                        "P2",
                        "在未知日人流量时直接反演使用时间，模型存在欠定与循环关系。",
                        "输入、参数和输出之间缺少闭合约束。",
                        "-6.0",
                        "$.findings[0]",
                        List.of("B1")
                )),
                "{}"
        );

        GroundedSuggestionV4Output output = assembler.assemble(
                new GroundedSuggestionV3Output(
                        "GROUNDED_SUGGESTION_V3",
                        "旧策略",
                        List.of(),
                        List.of(),
                        List.of()
                ),
                parse(),
                evidence,
                null
        );

        assertThat(output.items().get(0).guidanceMarkdown())
                .contains("未知量、约束条件、可辨识性和误差传播", "输入、参数、约束与输出")
                .doesNotContain("符号、参数、单位、量纲");
    }

    private GroundedSuggestionV4Output.Item itemForFinding(
            GroundedSuggestionV4Output output,
            String findingId
    ) {
        return output.items().stream()
                .filter(item -> item.evidenceChain().reviewFindingIds().contains(findingId))
                .findFirst()
                .orElseThrow();
    }

    private GroundedSuggestionV3Output.Item item(
            String id,
            String type,
            String priority,
            String action,
            List<String> findingIds
    ) {
        return item(id, type, priority, action, findingIds, List.of("KC-1"));
    }

    private GroundedSuggestionV3Output.Item item(
            String id,
            String type,
            String priority,
            String action,
            List<String> findingIds,
            List<String> citationIds
    ) {
        return new GroundedSuggestionV3Output.Item(
                id,
                priority,
                type,
                "MODEL",
                1,
                "模型说明",
                "原模型使用常量参数。",
                "当前说明不足以解释参数关系。",
                new GroundedSuggestionV3Output.TargetLocation(
                        List.of(5),
                        "模型建立",
                        List.of("B1")
                ),
                action,
                List.of("参数关系得到完整说明。"),
                new GroundedSuggestionV3Output.EvidenceChain(
                        List.of("B1"),
                        findingIds,
                        citationIds
                )
        );
    }

    private PaperParseDTO parse() {
        return parse("原模型使用常量参数。");
    }

    private PaperParseDTO parse(String text) {
        return new PaperParseDTO(
                1L,
                1L,
                "PAPER_PARSE_V2",
                "PAPER_DOCUMENT_V2",
                "sha",
                "SUCCESS",
                5,
                false,
                "{}",
                "{\"blocks\":[{\"blockId\":\"B1\",\"physicalPage\":5,"
                        + "\"type\":\"PARAGRAPH\",\"text\":"
                        + jsonString(text) + "}]}",
                null
        );
    }

    private PaperParseDTO parseTwoBlocks() {
        return new PaperParseDTO(
                1L,
                1L,
                "PAPER_PARSE_V2",
                "PAPER_DOCUMENT_V2",
                "sha",
                "SUCCESS",
                5,
                false,
                "{}",
                "{\"blocks\":["
                        + "{\"blockId\":\"B1\",\"physicalPage\":5,"
                        + "\"type\":\"FIGURE\",\"text\":\"Figure 1: workflow\"},"
                        + "{\"blockId\":\"B2\",\"physicalPage\":5,"
                        + "\"type\":\"FIGURE\",\"text\":\"Figure 1: work\"}]}",
                null
        );
    }

    private PaperParseDTO parseFiveBlocks() {
        return new PaperParseDTO(
                1L,
                1L,
                "PAPER_PARSE_V2",
                "PAPER_DOCUMENT_V2",
                "sha",
                "SUCCESS",
                5,
                false,
                "{}",
                "{\"blocks\":["
                        + "{\"blockId\":\"B1\",\"physicalPage\":1,\"type\":\"PARAGRAPH\","
                        + "\"text\":\"摘要只描述了研究目标。\"},"
                        + "{\"blockId\":\"B2\",\"physicalPage\":5,\"type\":\"FIGURE\","
                        + "\"text\":\"Figure 1: workflow\"},"
                        + "{\"blockId\":\"B3\",\"physicalPage\":8,\"type\":\"FORMULA\","
                        + "\"text\":\"x=y+z\"},"
                        + "{\"blockId\":\"B4\",\"physicalPage\":1,\"type\":\"PARAGRAPH\","
                        + "\"text\":\"摘要结论未给出正文对应位置。\"},"
                        + "{\"blockId\":\"B5\",\"physicalPage\":4,\"type\":\"PARAGRAPH\","
                        + "\"text\":\"符号 x 在正文中表示两种含义。\"}]}",
                null
        );
    }

    private ReviewEvidenceSnapshot.Finding finding(String id, String statement) {
        return finding(id, statement, List.of("B1"));
    }

    private ReviewEvidenceSnapshot.Finding finding(
            String id,
            String statement,
            List<String> paperEvidenceIds
    ) {
        return new ReviewEvidenceSnapshot.Finding(
                id,
                "ISSUE",
                "MODEL",
                "P1",
                statement,
                "-2.0",
                "$.findings[0]",
                paperEvidenceIds
        );
    }

    private GroundedSuggestionV3Output.Item issueItem(
            String suggestionId,
            String findingId,
            String guidance,
            String acceptanceCriterion,
            String paperEvidenceId
    ) {
        return new GroundedSuggestionV3Output.Item(
                suggestionId,
                "P1",
                "CORRECTION",
                guidance.contains("图号") ? "FIGURE" : "MODEL",
                1,
                "问题修正",
                "论文存在需要修正的问题。",
                "该问题会影响论证可复核性。",
                new GroundedSuggestionV3Output.TargetLocation(
                        List.of(5),
                        "正文",
                        List.of(paperEvidenceId)
                ),
                guidance,
                List.of(acceptanceCriterion),
                new GroundedSuggestionV3Output.EvidenceChain(
                        List.of(paperEvidenceId),
                        List.of(findingId),
                        List.of()
                )
        );
    }

    private String jsonString(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private KnowledgeRetrievalResultDTO knowledge() {
        return new KnowledgeRetrievalResultDTO(
                "run-1",
                "SUGGESTION_DEEP_RETRIEVAL_V1",
                "HYBRID",
                "index-v1",
                "manifest-v1",
                "source-v1",
                "COMPLETED",
                List.of(new KnowledgeCitationDTO(
                        "KC-1",
                        "D1",
                        "C1",
                        "模型参数说明",
                        "数学建模/论文评审",
                        "参数",
                        "hash",
                        "L2",
                        "适用于参数关系说明",
                        0.9,
                        "多个参数共同决定输出时，应说明来源、关系和不确定性。"
                ))
        );
    }

    private KnowledgeRetrievalResultDTO knowledgeWithExternalImages() {
        KnowledgeRetrievalResultDTO result = knowledge();
        result.getCitations().get(0).setContent(
                "应说明参数来源和关系。\n"
                        + "![参数图](https://example.com/parameter.png)\n"
                        + "<img src=\"https://example.com/parameter-2.png\">"
        );
        return result;
    }
}
