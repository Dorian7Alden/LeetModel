package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.config.ReviewV3Properties;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.parse.PaperParseService;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import com.leetmodel.review.parse.v2.PaperParseV2Parser;
import com.leetmodel.review.service.ReviewTaskLogService;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepEvidenceReviewV3WorkflowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaperParseService parseService;
    private ProblemFeignClient problemFeignClient;
    private ReviewTaskLogService logService;
    private AiClient aiClient;
    private DeepEvidenceReviewV3Workflow workflow;

    @BeforeEach
    void setUp() {
        parseService = mock(PaperParseService.class);
        problemFeignClient = mock(ProblemFeignClient.class);
        logService = mock(ReviewTaskLogService.class);
        aiClient = mock(AiClient.class);

        when(logService.start(any(), any(), any(), any())).thenReturn(new ReviewTaskLog());

        Phase1SliceExtractor sliceExtractor = new Phase1SliceExtractor();
        Phase1StructuralReviewOperator phase1Operator = new Phase1StructuralReviewOperator(aiClient, objectMapper, sliceExtractor);
        SubProblemClassifier classifier = new SubProblemClassifier();
        TaskPlannerOperator plannerOperator = new TaskPlannerOperator(aiClient, objectMapper, classifier);
        ContextSlicingEngine slicingEngine = new ContextSlicingEngine(null);
        SubTaskEvaluationWorker worker = new SubTaskEvaluationWorker(aiClient, objectMapper, slicingEngine);
        DeepEvidenceReviewV3Reducer reducer = new DeepEvidenceReviewV3Reducer();
        ReviewV3Properties properties = new ReviewV3Properties();

        workflow = new DeepEvidenceReviewV3Workflow(
                parseService,
                problemFeignClient,
                objectMapper,
                logService,
                phase1Operator,
                plannerOperator,
                worker,
                reducer,
                new SyncTaskExecutor(),
                properties
        );
    }

    @Test
    void shouldExecuteDeepEvidenceReviewV3EndToEnd() throws Exception {
        Long submissionId = 5001L;
        Long problemId = 101L;

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                submissionId,
                "sha256-hash",
                new PaperDocumentV2.DocumentMetadata(15, 12000, "测试数模论文", "ZH", "PAPER_PARSE_V2", "2026-09-05T12:00:00Z"),
                new PaperDocumentV2.LayoutAesthetics(92.0, "HIGH", "EXCELLENT", "排版极佳"),
                List.of(
                        new PaperDocumentV2.ContentBlockV2("B01", PaperDocumentV2.BlockType.HEADING, 1, "摘要",
                                new PaperDocumentV2.HeadingPayload(1, "", "摘要"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B02", PaperDocumentV2.BlockType.PARAGRAPH, 1, "摘要：针对问题一建立整数规划模型，计算得出最优费用32.4万元。", null, null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B03", PaperDocumentV2.BlockType.HEADING, 2, "模型假设",
                                new PaperDocumentV2.HeadingPayload(1, "", "模型假设"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B04", PaperDocumentV2.BlockType.PARAGRAPH, 2, "假设车辆速度恒定。", null, null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B05", PaperDocumentV2.BlockType.HEADING, 3, "符号说明",
                                new PaperDocumentV2.HeadingPayload(1, "", "符号说明"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B06", PaperDocumentV2.BlockType.TABLE, 3, "符号表", null, null,
                                new PaperDocumentV2.TablePayload("表1 符号", "TOP", "1", "<table><tr><td>c</td><td>成本</td></tr></table>", ""), null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B07", PaperDocumentV2.BlockType.HEADING, 4, "三、问题一求解",
                                new PaperDocumentV2.HeadingPayload(1, "三、", "问题一求解"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B08", PaperDocumentV2.BlockType.FORMULA, 4, "min sum c_ij x_ij", null,
                                new PaperDocumentV2.FormulaPayload("\\min \\sum c_{ij} x_{ij}", "(1)", false), null, null, null, List.of())
                ),
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC_01", "摘要", 1, "B01", 1),
                        new PaperDocumentV2.SectionIndex("SEC_02", "模型假设", 1, "B03", 2),
                        new PaperDocumentV2.SectionIndex("SEC_03", "符号说明", 1, "B05", 3),
                        new PaperDocumentV2.SectionIndex("SEC_04", "三、问题一求解", 1, "B07", 4)
                ),
                new PaperDocumentV2.DocumentQualityV2("SUCCESS", 15, 0, 5, 2, 2, 90.0, List.of())
        );

        String docJson = objectMapper.writeValueAsString(doc);
        PaperParseDTO parseDTO = new PaperParseDTO(
                9001L, submissionId, PaperParseV2Parser.WORKFLOW_VERSION,
                PaperDocumentV2.SCHEMA_VERSION, "sha256", "SUCCESS",
                15, false, "{}", docJson, null
        );
        when(parseService.ensure(submissionId, PaperParseV2Parser.WORKFLOW_VERSION)).thenReturn(parseDTO);

        ProblemContextDTO problem = new ProblemContextDTO(
                problemId, "赛题标题",
                "### 问题一\n建立优化模型求解最小费用。",
                180, 1
        );
        when(problemFeignClient.getProblemContext(problemId)).thenReturn(Result.ok(problem));

        // AI 调用模拟:
        // 1. 阶段一静态审查响应
        String phase1AiJson = """
                {
                  "score": 23.0,
                  "aspects": [
                    {"aspectCode": "ABSTRACT_STRUCTURE", "score": 9.0},
                    {"aspectCode": "PROBLEM_ANALYSIS_STRUCTURE", "score": 4.5},
                    {"aspectCode": "ASSUMPTION_NOMENCLATURE", "score": 4.5},
                    {"aspectCode": "CODE_LAYOUT_AESTHETICS", "score": 5.0}
                  ],
                  "findings": [
                    {"findingId": "F_P1_01", "type": "STRENGTH", "statement": "摘要规范，报出了具体数字32.4万元。", "blockId": "B02", "physicalPage": 1}
                  ]
                }
                """;

        // 2. 规划算子响应
        String plannerAiJson = """
                {
                  "tasks": [
                    {
                      "taskId": "TASK_ABSTRACT_VERIFY",
                      "taskType": "ABSTRACT_VERIFICATION",
                      "taskName": "摘要全文核验",
                      "targetQuestionNo": 0
                    },
                    {
                      "taskId": "TASK_Q1_EVAL",
                      "taskType": "SUB_PROBLEM_EVALUATION",
                      "taskName": "问题一求解核验",
                      "targetQuestionNo": 1
                    },
                    {
                      "taskId": "TASK_SENSITIVITY_EVAL",
                      "taskType": "SENSITIVITY_EVALUATION",
                      "taskName": "灵敏度检验",
                      "targetQuestionNo": 0
                    }
                  ]
                }
                """;

        // 3. Worker 响应
        String workerAiJson = """
                {
                  "score": 14.0,
                  "maxScore": 15.0,
                  "evaluationSummary": "推导严密",
                  "aspectScores": [
                    {"aspectCode": "FORMULATION", "maxScore": 7.5, "score": 7.0},
                    {"aspectCode": "ALGORITHM", "maxScore": 4.5, "score": 4.5},
                    {"aspectCode": "RESULT_ANALYSIS", "maxScore": 3.0, "score": 2.5}
                  ],
                  "observations": [
                    {"observationId": "OBS_1", "blockId": "B08", "physicalPage": 4, "observationType": "FORMULA", "summary": "目标函数"}
                  ],
                  "findings": [
                    {"findingId": "F_Q1_01", "type": "STRENGTH", "statement": "公式(1)形式严密", "blockId": "B08", "physicalPage": 4}
                  ]
                }
                """;

        when(aiClient.chat(any()))
                .thenReturn(new AiChatResponse("call-p1", AiProvider.NEW_API, "model", "r1", phase1AiJson, null, "stop", null))
                .thenReturn(new AiChatResponse("call-plan", AiProvider.NEW_API, "model", "r2", plannerAiJson, null, "stop", null))
                .thenReturn(new AiChatResponse("call-w1", AiProvider.NEW_API, "model", "r3", workerAiJson, null, "stop", null))
                .thenReturn(new AiChatResponse("call-w2", AiProvider.NEW_API, "model", "r4", workerAiJson, null, "stop", null))
                .thenReturn(new AiChatResponse("call-w3", AiProvider.NEW_API, "model", "r5", workerAiJson, null, "stop", null));

        ReviewTask task = new ReviewTask();
        task.setId(7001L);
        task.setAttemptNo(1);
        task.setWorkflowVersion(DeepEvidenceReviewV3Workflow.VERSION_CODE);

        SubmissionReviewDTO submission = new SubmissionReviewDTO(submissionId, 10L, problemId, 1, "object-key.pdf");

        ReviewWorkflowResult result = workflow.execute(task, submission);

        assertThat(result).isNotNull();
        assertThat(result.score()).isNotNull();
        assertThat(result.score()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.parseArtifactId()).isEqualTo(9001L);
        assertThat(result.resultJson()).contains("DIM_MATHEMATICAL_MODELING");
        assertThat(result.resultJson()).contains("DIM_STRUCTURE_WRITING");
        assertThat(result.resultJson()).contains("DIM_ALGORITHM_SOLUTION");
        assertThat(result.resultJson()).contains("DIM_RESULT_VALIDATION");
        assertThat(result.resultJson()).contains("DIM_ASSUMPTION_UNDERSTANDING");
    }

    @Test
    void shouldThrowExceptionWhenParseStatusFails() {
        Long submissionId = 5002L;
        PaperParseDTO parseDTO = new PaperParseDTO(
                9002L, submissionId, PaperParseV2Parser.WORKFLOW_VERSION,
                PaperDocumentV2.SCHEMA_VERSION, "sha256", "FAILED",
                0, false, "{}", "{}", "解析错误"
        );
        when(parseService.ensure(submissionId, PaperParseV2Parser.WORKFLOW_VERSION)).thenReturn(parseDTO);

        ReviewTask task = new ReviewTask();
        task.setId(7002L);
        SubmissionReviewDTO submission = new SubmissionReviewDTO(submissionId, 10L, 101L, 1, "key");

        assertThrows(IllegalArgumentException.class, () -> workflow.execute(task, submission));
    }
}
