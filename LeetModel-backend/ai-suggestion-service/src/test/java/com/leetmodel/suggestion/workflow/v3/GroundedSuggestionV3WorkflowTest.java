package com.leetmodel.suggestion.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroundedSuggestionV3WorkflowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiClient aiClient;
    private KnowledgeRetrievalFeignClient knowledgeFeignClient;
    private Executor directExecutor;
    private GroundedSuggestionV3Workflow workflow;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        knowledgeFeignClient = mock(KnowledgeRetrievalFeignClient.class);
        directExecutor = new SyncTaskExecutor();
        workflow = new GroundedSuggestionV3Workflow(
                aiClient, objectMapper, knowledgeFeignClient, directExecutor);
    }

    @Test
    void shouldExecuteFullV3WorkflowWithPlanningSubTasksAndSynthesis() throws Exception {
        String plannerJson = """
                {
                  "tasks": [
                    {
                      "taskId": "SUG_TASK_STRUCT",
                      "taskType": "STRUCTURAL_SUGGESTION",
                      "taskName": "全篇结构规范自查",
                      "targetQuestionNo": 0,
                      "categoryCode": "WRITING",
                      "suggestedSectionIds": ["SEC_01"],
                      "suggestionObjectives": ["自查摘要要素与符号量纲"]
                    },
                    {
                      "taskId": "SUG_TASK_Q1",
                      "taskType": "SUB_PROBLEM_SUGGESTION",
                      "taskName": "问题一建模推演与升华",
                      "targetQuestionNo": 1,
                      "categoryCode": "OPTIMIZATION",
                      "suggestedSectionIds": ["SEC_02"],
                      "suggestionObjectives": ["优化动态容量约束"]
                    }
                  ]
                }
                """;

        String subTaskJson = """
                {
                  "taskId": "SUG_TASK_Q1",
                  "executionStatus": "SUCCESS",
                  "suggestions": [
                    {
                      "suggestionId": "TEMP-1",
                      "priority": "P1",
                      "type": "CORRECTION",
                      "category": "MODEL",
                      "subProblemNo": 1,
                      "title": "容量约束时间切片修正",
                      "problemOrGap": "未考虑早晚高峰动态容量约束",
                      "diagnosis": "静态常数导致高峰期超载",
                      "targetLocation": {
                        "physicalPages": [5],
                        "section": "3.2 模型建立",
                        "anchorBlockIds": ["B0024"]
                      },
                      "actionPlanMarkdown": "#### 1. 现状\\n静态容量\\n\\n#### 2. 方案\\n$$\\\\sum x_{ijt} \\\\le C_t$$",
                      "acceptanceCriteria": ["残差小于 1e-4"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B0024"],
                        "reviewFindingIds": ["F_Q1_001"],
                        "knowledgeCitationIds": ["KC-001"]
                      }
                    }
                  ]
                }
                """;

        String synthJson = """
                {
                  "workflowVersion": "GROUNDED_SUGGESTION_V3",
                  "overallStrategy": "### 整体修改战略\\n优先修复第一问容量约束，并补充基准模型对比。",
                  "topPriorities": ["修复问题一容量约束时间切片公式"],
                  "subTaskSummaries": [],
                  "items": [
                    {
                      "suggestionId": "S-1",
                      "priority": "P1",
                      "type": "CORRECTION",
                      "category": "MODEL",
                      "subProblemNo": 1,
                      "title": "容量约束时间切片修正",
                      "problemOrGap": "未考虑早晚高峰动态容量约束",
                      "diagnosis": "静态常数导致高峰期超载",
                      "targetLocation": {
                        "physicalPages": [5],
                        "section": "3.2 模型建立",
                        "anchorBlockIds": ["B0024"]
                      },
                      "actionPlanMarkdown": "#### 1. 现状\\n静态容量\\n\\n#### 2. 方案\\n$$\\\\sum x_{ijt} \\\\le C_t$$",
                      "acceptanceCriteria": ["残差小于 1e-4"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B0024"],
                        "reviewFindingIds": ["F_Q1_001"],
                        "knowledgeCitationIds": ["KC-001"]
                      }
                    }
                  ]
                }
                """;

        when(aiClient.chat(any())).thenReturn(
                resp(plannerJson),
                resp(subTaskJson),
                resp(subTaskJson),
                resp(synthJson)
        );

        when(knowledgeFeignClient.retrieve(any())).thenReturn(Result.ok(new KnowledgeRetrievalResultDTO(
                "run-1", "SUGGESTION_DEEP_RETRIEVAL_V1", "VECTOR+BM25", "idx", "man", "src", "COMPLETED",
                List.of(new KnowledgeCitationDTO("KC-001", "doc-1", "chunk-1", "优化速查", "path", null, "hash", "L4", "GENERAL_MODELING", 1.0, "动态规划"))
        )));

        SuggestionTask task = new SuggestionTask();
        task.setId(9001L);
        task.setSubmissionId(101L);
        ProblemContextDTO problem = new ProblemContextDTO();
        problem.setId(51L);
        problem.setTitle("测试赛题");
        problem.setContentMarkdown("请针对车辆配送进行动态路径规划");
        PaperParseDTO parse = new PaperParseDTO();
        parse.setSubmissionId(101L);
        parse.setArtifactId(201L);
        parse.setWorkflowVersion("PAPER_PARSE_V2");
        parse.setStatus("SUCCESS");
        parse.setPageCount(10);
        parse.setDocumentJson("""
                {"sections":[{"sectionId":"SEC_01","title":"摘要","headingBlockId":"B1","physicalPage":1}],
                 "blocks":[{"blockId":"B0024","type":"PARAGRAPH","physicalPage":5,"text":"模型建立部分公式3"}]}
                """);
        ReviewEvidenceSnapshot reviewEvidence = new ReviewEvidenceSnapshot(
                501L, 501L, "DEEP_EVIDENCE_REVIEW_V3", null,
                List.of(new ReviewEvidenceSnapshot.Finding("F_Q1_001", "ISSUE", "MODEL", "HIGH", "约束不完整", "-2.0 分", "$.path", List.of("B0024"))),
                "{\"findings\":[]}");

        SuggestionWorkflowResult result = workflow.execute(task, problem, parse, reviewEvidence);

        assertThat(result).isNotNull();
        assertThat(result.resultJson()).contains("GROUNDED_SUGGESTION_V3");
        GroundedSuggestionV3Output output = objectMapper.readValue(result.resultJson(), GroundedSuggestionV3Output.class);
        assertThat(output.overallStrategy()).contains("整体修改战略");
        assertThat(output.topPriorities()).contains("修复问题一容量约束时间切片公式");
        assertThat(output.items()).hasSize(1);
        assertThat(output.items().get(0).suggestionId()).isEqualTo("S-1");
        assertThat(output.items().get(0).type()).isEqualTo("CORRECTION");
        assertThat(output.items().get(0).actionPlanMarkdown()).contains("\\sum x_{ijt}");
    }

    @Test
    void shouldIsolateDegradedSubTaskWhenOneFails() throws Exception {
        String plannerJson = """
                {
                  "tasks": [
                    {
                      "taskId": "TASK_OK",
                      "taskType": "STRUCTURAL_SUGGESTION",
                      "taskName": "规范建议",
                      "targetQuestionNo": 0,
                      "categoryCode": "WRITING",
                      "suggestedSectionIds": [],
                      "suggestionObjectives": ["规范"]
                    },
                    {
                      "taskId": "TASK_FAIL",
                      "taskType": "SUB_PROBLEM_SUGGESTION",
                      "taskName": "小题建议",
                      "targetQuestionNo": 1,
                      "categoryCode": "OPTIMIZATION",
                      "suggestedSectionIds": [],
                      "suggestionObjectives": ["优化"]
                    }
                  ]
                }
                """;

        String subTaskOkJson = """
                {
                  "taskId": "TASK_OK",
                  "executionStatus": "SUCCESS",
                  "suggestions": [
                    {
                      "suggestionId": "S-1",
                      "priority": "P2",
                      "type": "ADVANCEMENT",
                      "category": "WRITING",
                      "subProblemNo": 0,
                      "title": "三线表规范化",
                      "problemOrGap": "表格缺少规范表头",
                      "diagnosis": "形式规范",
                      "targetLocation": {
                        "physicalPages": [2],
                        "section": "2 假设",
                        "anchorBlockIds": []
                      },
                      "actionPlanMarkdown": "调整为三线表",
                      "acceptanceCriteria": ["符合规范"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B1"],
                        "reviewFindingIds": [],
                        "knowledgeCitationIds": ["KC-001"]
                      }
                    }
                  ]
                }
                """;

        // Planner 成功 -> 第一个子任务成功 -> 第二个子任务抛异常 -> 汇总成功
        when(aiClient.chat(any()))
                .thenReturn(resp(plannerJson))
                .thenReturn(resp(subTaskOkJson))
                .thenThrow(new RuntimeException("模型调用网络波动"))
                .thenReturn(resp("""
                        {
                          "workflowVersion": "GROUNDED_SUGGESTION_V3",
                          "overallStrategy": "局部降级汇总策略",
                          "topPriorities": ["完善规范"],
                          "subTaskSummaries": [],
                          "items": [
                            {
                              "suggestionId": "S-1",
                              "priority": "P2",
                              "type": "ADVANCEMENT",
                              "category": "WRITING",
                              "subProblemNo": 0,
                              "title": "三线表规范化",
                              "problemOrGap": "表格缺少规范表头",
                              "diagnosis": "形式规范",
                              "targetLocation": {
                                "physicalPages": [2],
                                "section": "2 假设",
                                "anchorBlockIds": []
                              },
                              "actionPlanMarkdown": "调整为三线表",
                              "acceptanceCriteria": ["符合规范"],
                              "evidenceChain": {
                                "paperEvidenceIds": ["B1"],
                                "reviewFindingIds": [],
                                "knowledgeCitationIds": ["KC-001"]
                              }
                            }
                          ]
                        }
                        """));

        SuggestionTask task = new SuggestionTask();
        task.setId(9002L);
        ProblemContextDTO problem = new ProblemContextDTO();
        problem.setId(51L);
        problem.setTitle("赛题");
        problem.setContentMarkdown("题面");
        PaperParseDTO parse = new PaperParseDTO();
        parse.setSubmissionId(102L);
        parse.setArtifactId(202L);
        parse.setWorkflowVersion("PAPER_PARSE_V2");
        parse.setStatus("SUCCESS");
        parse.setPageCount(5);
        parse.setDocumentJson("{\"sections\":[],\"blocks\":[]}");
        ReviewEvidenceSnapshot reviewEvidence = new ReviewEvidenceSnapshot(502L, 502L, "DEEP_EVIDENCE_REVIEW_V3", null, List.of(), "{}");

        SuggestionWorkflowResult result = workflow.execute(task, problem, parse, reviewEvidence);

        assertThat(result).isNotNull();
        GroundedSuggestionV3Output output = objectMapper.readValue(result.resultJson(), GroundedSuggestionV3Output.class);
        assertThat(output.items()).hasSize(1);
        assertThat(output.subTaskSummaries()).hasSize(2);
        assertThat(output.subTaskSummaries()).extracting(GroundedSuggestionV3Output.SubTaskSummary::status)
                .contains("SUCCESS", "DEGRADED");
    }

    private AiChatResponse resp(String json) {
        return new AiChatResponse("call-id", AiProvider.NEW_API, "model", "resp-id", json, null, "stop", null);
    }
}
