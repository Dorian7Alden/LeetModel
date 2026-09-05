package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.TaskPlanResultDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskPlannerOperatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubProblemClassifier classifier = new SubProblemClassifier();

    @Test
    void shouldPlanTasksAndReconcileMissingTasks() {
        AiClient aiClient = mock(AiClient.class);
        String jsonResponse = """
                {
                  "tasks": [
                    {
                      "taskId": "TASK_ABSTRACT_VERIFY",
                      "taskType": "ABSTRACT_VERIFICATION",
                      "taskName": "摘要全文对照",
                      "targetQuestionNo": 0
                    },
                    {
                      "taskId": "TASK_Q1_EVAL",
                      "taskType": "SUB_PROBLEM_EVALUATION",
                      "taskName": "问题一建模",
                      "targetQuestionNo": 1
                    }
                  ]
                }
                """;
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-plan", AiProvider.NEW_API, "deepseek-chat", "resp-p", jsonResponse, null, "stop", null));

        TaskPlannerOperator operator = new TaskPlannerOperator(aiClient, objectMapper, classifier);

        ReviewTask task = new ReviewTask();
        task.setId(2001L);
        task.setAttemptNo(1);

        ProblemContextDTO problem = new ProblemContextDTO(
                501L, "测试赛题",
                "### 问题一\n建立预测模型\n\n### 问题二\n建立优化调度模型",
                180, 1
        );

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION, 2001L, "sha256",
                null, null, List.of(),
                List.of(
                        new PaperDocumentV2.SectionIndex("S1", "摘要", 1, "B1", 1),
                        new PaperDocumentV2.SectionIndex("S2", "问题一求解", 1, "B2", 3),
                        new PaperDocumentV2.SectionIndex("S3", "问题二求解", 1, "B3", 5),
                        new PaperDocumentV2.SectionIndex("S4", "灵敏度分析", 1, "B4", 7)
                ),
                null
        );

        TaskPlanResultDTO plan = operator.plan(task, problem, doc);

        assertThat(plan).isNotNull();
        // 应包含摘要、问题一、自动补全的问题二、以及灵敏度任务
        assertThat(plan.getTasks().stream().anyMatch(t -> "ABSTRACT_VERIFICATION".equals(t.getTaskType()))).isTrue();
        assertThat(plan.getTasks().stream().anyMatch(t -> Integer.valueOf(1).equals(t.getTargetQuestionNo()))).isTrue();
        assertThat(plan.getTasks().stream().anyMatch(t -> Integer.valueOf(2).equals(t.getTargetQuestionNo()))).isTrue();
        assertThat(plan.getTasks().stream().anyMatch(t -> "SENSITIVITY_EVALUATION".equals(t.getTaskType()))).isTrue();
    }
}
