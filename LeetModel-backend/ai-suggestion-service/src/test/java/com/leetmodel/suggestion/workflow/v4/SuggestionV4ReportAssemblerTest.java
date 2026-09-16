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
    void discardsCandidateWhenKnowledgeCitationDoesNotMatchSnapshot() throws Exception {
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

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assembler.assemble(
                        source,
                        parse(),
                        new ReviewEvidenceSnapshot(1L, 1L, "DEEP_EVIDENCE_REVIEW_V4",
                                null, List.of(), "{}"),
                        knowledge()
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少符合 V4 语义边界");
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
                        + "\"type\":\"PARAGRAPH\",\"text\":\"原模型使用常量参数。\"}]}",
                null
        );
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
}
