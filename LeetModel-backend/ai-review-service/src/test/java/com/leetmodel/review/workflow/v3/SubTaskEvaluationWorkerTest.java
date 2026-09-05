package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubProblemCategoryDTO;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;
import com.leetmodel.common.api.dto.SubTaskPlanDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubTaskEvaluationWorkerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExecuteSubTaskSuccessfully() {
        AiClient aiClient = mock(AiClient.class);
        String jsonResponse = """
                {
                  "score": 13.5,
                  "maxScore": 15.0,
                  "evaluationSummary": "模型推导严密",
                  "aspectScores": [
                    {"aspectCode": "FORMULATION", "score": 7.0},
                    {"aspectCode": "ALGORITHM", "score": 4.0},
                    {"aspectCode": "RESULT_ANALYSIS", "score": 2.5}
                  ],
                  "observations": [
                    {"observationId": "OBS_1", "blockId": "B1", "physicalPage": 3, "summary": "公式1"}
                  ],
                  "findings": [
                    {"findingId": "F_Q1_001", "statement": "推导规范", "blockId": "B1"}
                  ]
                }
                """;
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-sub", AiProvider.NEW_API, "deepseek-chat", "resp-s", jsonResponse, null, "stop", null));

        ContextSlicingEngine slicingEngine = new ContextSlicingEngine(null);
        SubTaskEvaluationWorker worker = new SubTaskEvaluationWorker(aiClient, objectMapper, slicingEngine);

        ReviewTask task = new ReviewTask();
        task.setId(3001L);
        task.setAttemptNo(1);

        SubTaskPlanDTO plan = SubTaskPlanDTO.builder()
                .taskId("TASK_Q1_EVAL")
                .taskType("SUB_PROBLEM_EVALUATION")
                .taskName("问题一模型求解审查")
                .targetQuestionNo(1)
                .subProblemCategory(SubProblemCategoryDTO.builder().categoryCode("OPTIMIZATION").categoryName("运筹优化类").build())
                .build();

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION, 3001L, "sha256",
                null, null, List.of(), List.of(), null
        );

        ProblemContextDTO problem = new ProblemContextDTO(1L, "题目", "题面", 180, 1);

        SubTaskEvaluationResultDTO result = worker.execute(task, plan, doc, problem);

        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo("SUCCESS");
        assertThat(result.getScore()).isEqualTo(BigDecimal.valueOf(13.5).setScale(1));
        assertThat(result.getFindings()).hasSize(1);
    }

    @Test
    void shouldDegradeGracefullyWhenAiCallFails() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.chat(any())).thenThrow(new RuntimeException("模型调用超时或网关不可用"));

        ContextSlicingEngine slicingEngine = new ContextSlicingEngine(null);
        SubTaskEvaluationWorker worker = new SubTaskEvaluationWorker(aiClient, objectMapper, slicingEngine);

        ReviewTask task = new ReviewTask();
        task.setId(3002L);
        task.setAttemptNo(1);

        SubTaskPlanDTO plan = SubTaskPlanDTO.builder()
                .taskId("TASK_Q2_EVAL")
                .taskType("SUB_PROBLEM_EVALUATION")
                .taskName("问题二求解审查")
                .targetQuestionNo(2)
                .build();

        PaperDocumentV2 doc = new PaperDocumentV2(PaperDocumentV2.SCHEMA_VERSION, 3002L, "sha", null, null, List.of(), List.of(), null);
        ProblemContextDTO problem = new ProblemContextDTO(1L, "题目", "题面", 180, 1);

        SubTaskEvaluationResultDTO result = worker.execute(task, plan, doc, problem);

        assertThat(result).isNotNull();
        assertThat(result.getExecutionStatus()).isEqualTo("DEGRADED");
        // 降级分应为 maxScore(15.0) * 0.6 = 9.0
        assertThat(result.getScore()).isEqualTo(BigDecimal.valueOf(9.0).setScale(1));
        assertThat(result.getFindings().get(0).getType()).isEqualTo("ISSUE");
    }
}
