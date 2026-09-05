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

        assertThat(assembled.getKnowledgeCitations()).isNotEmpty();
        assertThat(assembled.getKnowledgeCitations().get(0).getTitle()).isEqualTo("运筹优化评讲");

        assertThat(assembled.getProblemQuestionMarkdown()).contains("最小化运输成本");
    }
}
