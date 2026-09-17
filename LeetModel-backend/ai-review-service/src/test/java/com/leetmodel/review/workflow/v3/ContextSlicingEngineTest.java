package com.leetmodel.review.workflow.v3;

import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubProblemCategoryDTO;
import com.leetmodel.common.api.dto.SubTaskPlanDTO;
import com.leetmodel.common.api.dto.TaskAssembledContextDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextSlicingEngineTest {

    @Test
    void shouldAssembleContextWithMandatoryAssumptionsAndTargetBlocks() {
        KnowledgeRetrievalFeignClient feignClient = mock(KnowledgeRetrievalFeignClient.class);
        when(feignClient.retrieve(any())).thenReturn(Result.ok(new KnowledgeRetrievalResultDTO(
                "run-1", "AI_DIRECTORY_V1", "DIRECTORY", null, null, null, "COMPLETED",
                List.of(new KnowledgeCitationDTO("KC-1", "D1", "C1", "运筹优化评讲", "path/to", null, "h1", "L3", "GENERAL", 0.9, "规划模型三要素必须清晰"))
        )));

        ContextSlicingEngine engine = new ContextSlicingEngine(feignClient);

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                2001L,
                "sha256-test",
                new PaperDocumentV2.DocumentMetadata(20, 15000, "优化建模论文", "ZH", "PAPER_PARSE_V2", "2026-09-05T12:00:00Z"),
                new PaperDocumentV2.LayoutAesthetics(90.0, "HIGH", "EXCELLENT", "极好"),
                List.of(
                        new PaperDocumentV2.ContentBlockV2("B01", PaperDocumentV2.BlockType.HEADING, 2, "模型假设",
                                new PaperDocumentV2.HeadingPayload(1, "", "模型假设"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B02", PaperDocumentV2.BlockType.PARAGRAPH, 2, "假设车辆均为同质纯电动车。", null, null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B03", PaperDocumentV2.BlockType.HEADING, 3, "符号说明",
                                new PaperDocumentV2.HeadingPayload(1, "", "符号说明"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B04", PaperDocumentV2.BlockType.TABLE, 3, "符号表", null, null,
                                new PaperDocumentV2.TablePayload("符号表", "TOP", "1", "<table><tr><td>x_{ij}</td><td>0-1决策变量</td></tr></table>", ""), null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B05", PaperDocumentV2.BlockType.HEADING, 4, "三、问题一求解模型",
                                new PaperDocumentV2.HeadingPayload(1, "三、", "问题一求解模型"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B06", PaperDocumentV2.BlockType.FORMULA, 4, "min sum c_ij x_ij", null,
                                new PaperDocumentV2.FormulaPayload("\\min \\sum c_{ij} x_{ij}", "(1)", false), null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B07", PaperDocumentV2.BlockType.HEADING, 6, "四、问题二求解模型",
                                new PaperDocumentV2.HeadingPayload(1, "四、", "问题二求解模型"), null, null, null, null, List.of())
                ),
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC_ASSUME", "模型假设", 1, "B01", 2),
                        new PaperDocumentV2.SectionIndex("SEC_NOMEN", "符号说明", 1, "B03", 3),
                        new PaperDocumentV2.SectionIndex("SEC_Q1", "三、问题一求解模型", 1, "B05", 4),
                        new PaperDocumentV2.SectionIndex("SEC_Q2", "四、问题二求解模型", 1, "B07", 6)
                ),
                new PaperDocumentV2.DocumentQualityV2("SUCCESS", 20, 0, 8, 3, 2, 88.0, List.of())
        );

        SubTaskPlanDTO taskPlan = SubTaskPlanDTO.builder()
                .taskId("TASK_Q1_EVAL")
                .taskType("SUB_PROBLEM_EVALUATION")
                .taskName("问题一运筹模型建立与求解")
                .targetQuestionNo(1)
                .subProblemCategory(SubProblemCategoryDTO.builder()
                        .categoryCode("OPTIMIZATION")
                        .categoryName("运筹优化类")
                        .retrievalScene("SCENE_REVIEW_OPTIMIZATION")
                        .build())
                .suggestedSectionAnchors(List.of(
                        SubTaskPlanDTO.SectionAnchorDTO.builder()
                                .sectionId("SEC_Q1")
                                .startBlockId("B05")
                                .endBlockId("B07")
                                .build()
                ))
                .build();

        ProblemContextDTO problem = new ProblemContextDTO(101L, "赛题1", "### 问题一\n请建立优化模型，最小化运输成本。", 180, 1);

        TaskAssembledContextDTO assembled = engine.assembleContext(doc, taskPlan, problem);

        assertThat(assembled.getAttachedAssumptions()).isNotEmpty();
        assertThat(assembled.getAttachedAssumptions().get(0).getText()).contains("纯电动车");

        assertThat(assembled.getAttachedNomenclature()).isNotEmpty();
        assertThat(assembled.getAttachedNomenclature().get(0).getHtmlTable()).contains("x_{ij}");

        assertThat(assembled.getTargetSectionBlocks()).isNotEmpty();
        assertThat(assembled.getTargetSectionBlocks().stream().anyMatch(b -> b.getLatex() != null && b.getLatex().contains("\\min"))).isTrue();
        assertThat(assembled.getTargetSectionBlocks())
                .noneMatch(block -> "B07".equals(block.getBlockId()));

        assertThat(assembled.getKnowledgeCitations()).isNotEmpty();
        assertThat(assembled.getKnowledgeCitations().get(0).getTitle()).isEqualTo("运筹优化评讲");

        assertThat(assembled.getProblemQuestionMarkdown()).contains("最小化运输成本");
    }

    @Test
    void shouldRecognizeEnglishAssumptionsAndNotations() {
        ContextSlicingEngine engine = new ContextSlicingEngine(null);
        PaperDocumentV2 document = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                2002L,
                "sha256-en",
                new PaperDocumentV2.DocumentMetadata(
                        6,
                        5000,
                        "English Paper",
                        "EN",
                        "PAPER_PARSE_V2",
                        "2026-09-16T10:00:00Z"
                ),
                null,
                List.of(
                        heading("B1", 1, 2, "Notations"),
                        paragraph("B2", 1, "The symbols are defined below."),
                        table("B3", 1),
                        heading("B4", 2, 2, "Assumptions"),
                        paragraph("B5", 2, "Assumption 1: walking positions are normally distributed."),
                        heading("B6", 3, 2, "Model I: Daily Foot Traffic"),
                        formula("B7", 3),
                        heading("B8", 4, 2, "Model II")
                ),
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC-1", "Notations", 2, "B1", 1),
                        new PaperDocumentV2.SectionIndex("SEC-2", "Assumptions", 2, "B4", 2),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-3",
                                "Model I: Daily Foot Traffic",
                                2,
                                "B6",
                                3
                        ),
                        new PaperDocumentV2.SectionIndex("SEC-4", "Model II", 2, "B8", 4)
                ),
                null
        );
        SubTaskPlanDTO taskPlan = SubTaskPlanDTO.builder()
                .taskId("TASK_Q1_EVAL")
                .taskType("SUB_PROBLEM_EVALUATION")
                .targetQuestionNo(1)
                .suggestedSectionAnchors(List.of(
                        SubTaskPlanDTO.SectionAnchorDTO.builder()
                                .sectionId("SEC-3")
                                .startBlockId("B6")
                                .endBlockId("B8")
                                .build()
                ))
                .build();

        TaskAssembledContextDTO assembled = engine.assembleContext(
                document,
                taskPlan,
                new ProblemContextDTO(1L, "Problem", "Question 1: estimate traffic.", 180, 1)
        );

        assertThat(assembled.getAttachedNomenclature())
                .extracting(TaskAssembledContextDTO.PaperSliceBlockDTO::getBlockId)
                .contains("B2", "B3");
        assertThat(assembled.getAttachedAssumptions())
                .extracting(TaskAssembledContextDTO.PaperSliceBlockDTO::getBlockId)
                .contains("B5");
        assertThat(assembled.getTargetSectionBlocks())
                .extracting(TaskAssembledContextDTO.PaperSliceBlockDTO::getBlockId)
                .containsExactly("B6", "B7");
    }

    private PaperDocumentV2.ContentBlockV2 heading(
            String blockId,
            int page,
            int level,
            String title
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.HEADING,
                page,
                title,
                new PaperDocumentV2.HeadingPayload(level, "", title),
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

    private PaperDocumentV2.ContentBlockV2 table(String blockId, int page) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.TABLE,
                page,
                "Notation table",
                null,
                null,
                new PaperDocumentV2.TablePayload(
                        "Notation table",
                        "TOP",
                        "1",
                        "<table><tr><td>n</td><td>traffic</td></tr></table>",
                        ""
                ),
                null,
                null,
                List.of()
        );
    }

    private PaperDocumentV2.ContentBlockV2 formula(String blockId, int page) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.FORMULA,
                page,
                "n = VH / KPL",
                null,
                new PaperDocumentV2.FormulaPayload("n = VH / KPL", "(1)", false),
                null,
                null,
                null,
                List.of()
        );
    }
}
