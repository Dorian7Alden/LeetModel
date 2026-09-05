package com.leetmodel.review.workflow.v3;

import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeepEvidenceReviewV3ReducerTest {

    private final DeepEvidenceReviewV3Reducer reducer = new DeepEvidenceReviewV3Reducer();

    @Test
    void shouldReduceScoresAndEnforceStrictArithmeticAssertion() {
        Phase1StructuralReviewResultDTO phase1 = Phase1StructuralReviewResultDTO.builder()
                .score(BigDecimal.valueOf(23.5))
                .maxScore(BigDecimal.valueOf(25.0))
                .aspects(List.of(
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("ABSTRACT_STRUCTURE").score(BigDecimal.valueOf(9.0)).build(),
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("PROBLEM_ANALYSIS_STRUCTURE").score(BigDecimal.valueOf(4.5)).build(),
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("ASSUMPTION_NOMENCLATURE").score(BigDecimal.valueOf(4.5)).build(),
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("CODE_LAYOUT_AESTHETICS").score(BigDecimal.valueOf(4.5)).build()
                ))
                .findings(List.of(
                        Phase1StructuralReviewResultDTO.StructuralFindingDTO.builder()
                                .findingId("F_P1_001").type("STRENGTH").statement("摘要优秀").blockId("B1").physicalPage(1).build()
                ))
                .build();

        List<SubTaskEvaluationResultDTO> phase2 = List.of(
                SubTaskEvaluationResultDTO.builder()
                        .taskId("TASK_ABSTRACT_VERIFY")
                        .taskType("ABSTRACT_VERIFICATION")
                        .score(BigDecimal.valueOf(9.0))
                        .maxScore(BigDecimal.valueOf(10.0))
                        .findings(List.of(
                                SubTaskEvaluationResultDTO.SubTaskFindingDTO.builder()
                                        .findingId("F_ABS_01").type("STRENGTH").statement("数值吻合").blockId("B1").physicalPage(1).build()
                        ))
                        .build(),
                SubTaskEvaluationResultDTO.builder()
                        .taskId("TASK_Q1_EVAL")
                        .taskType("SUB_PROBLEM_EVALUATION")
                        .targetQuestionNo(1)
                        .score(BigDecimal.valueOf(14.0))
                        .maxScore(BigDecimal.valueOf(15.0))
                        .aspectScores(List.of(
                                SubTaskEvaluationResultDTO.SubTaskAspectScoreDTO.builder()
                                        .aspectCode("FORMULATION").maxScore(BigDecimal.valueOf(7.5)).score(BigDecimal.valueOf(7.0)).build(),
                                SubTaskEvaluationResultDTO.SubTaskAspectScoreDTO.builder()
                                        .aspectCode("ALGORITHM").maxScore(BigDecimal.valueOf(4.5)).score(BigDecimal.valueOf(4.0)).build()
                        ))
                        .observations(List.of(
                                SubTaskEvaluationResultDTO.SubTaskObservationDTO.builder()
                                        .observationId("OBS_1").blockId("B10").physicalPage(3).observationType("FORMULA").summary("目标函数").build()
                        ))
                        .findings(List.of(
                                SubTaskEvaluationResultDTO.SubTaskFindingDTO.builder()
                                        .findingId("F_Q1_01").type("STRENGTH").statement("公式严密").blockId("B10").physicalPage(3).build()
                        ))
                        .build(),
                SubTaskEvaluationResultDTO.builder()
                        .taskId("TASK_SENSITIVITY_EVAL")
                        .taskType("SENSITIVITY_EVALUATION")
                        .score(BigDecimal.valueOf(13.0))
                        .maxScore(BigDecimal.valueOf(15.0))
                        .findings(List.of())
                        .build()
        );

        ProblemContextDTO problem = new ProblemContextDTO(1L, "赛题", "### 问题一\n求解优化", 180, 1);
        PaperDocumentV2 doc = new PaperDocumentV2(PaperDocumentV2.SCHEMA_VERSION, 1L, "sha", null, null, List.of(), List.of(), null);

        DeepEvidenceReviewV3Output output = reducer.reduce(phase1, phase2, problem, doc);

        assertThat(output).isNotNull();
        assertThat(output.getDimensions()).hasSize(5);

        // 核心算术断言检查: totalScore == sum(dimensions.score)
        BigDecimal sum = BigDecimal.ZERO;
        for (var dim : output.getDimensions()) {
            sum = sum.add(dim.getScore());
        }
        assertThat(output.getScore()).isEqualTo(sum);

        assertThat(output.getFindings()).isNotEmpty();
        assertThat(output.getObservations()).isNotEmpty();
        assertThat(output.getAnchors()).isNotEmpty();
        assertThat(output.getRequirementCoverage()).isNotEmpty();
    }
}
