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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 基于实际赛题与高保真切片资产的建议 V3 全链路端到端模拟测试。
 */
class SuggestionV3EndToEndSmokeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiClient aiClient;
    private KnowledgeRetrievalFeignClient knowledgeFeignClient;
    private GroundedSuggestionV3Workflow workflow;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        knowledgeFeignClient = mock(KnowledgeRetrievalFeignClient.class);
        workflow = new GroundedSuggestionV3Workflow(
                aiClient, objectMapper, knowledgeFeignClient, new SyncTaskExecutor());
    }

    @Test
    void shouldRunEndToEndSuggestionPipelineWithRealProblemAndParseArtifacts() throws Exception {
        // 1. 加载真实赛题题面
        String problemMarkdown = "2025 MCM 问题 A：台阶的持续磨损预测与人流偏好建模分析。";
        Path problemPath = Path.of("../../data/problem-01/problem.md");
        if (Files.exists(problemPath)) {
            problemMarkdown = Files.readString(problemPath);
        }

        // 2. 模拟规划算子、分任务教练与汇总 AI 的典型响应
        String plannerJson = """
                {
                  "tasks": [
                    {
                      "taskId": "SUG_STRUCT",
                      "taskType": "STRUCTURAL_SUGGESTION",
                      "taskName": "全篇结构自查与摘要数值补齐",
                      "targetQuestionNo": 0,
                      "categoryCode": "WRITING",
                      "suggestedSectionIds": ["SEC_01"],
                      "suggestionObjectives": ["补全摘要核心数字", "符号表增加国际单位"]
                    },
                    {
                      "taskId": "SUG_Q1",
                      "taskType": "SUB_PROBLEM_SUGGESTION",
                      "taskName": "台阶磨损机理动力学建模推演",
                      "targetQuestionNo": 1,
                      "categoryCode": "MECHANISM",
                      "suggestedSectionIds": ["SEC_03"],
                      "suggestionObjectives": ["修正磨损偏微分方程边界条件", "引入均匀磨损基准对比"]
                    }
                  ]
                }
                """;

        String subTaskStructJson = """
                {
                  "taskId": "SUG_STRUCT",
                  "executionStatus": "SUCCESS",
                  "suggestions": [
                    {
                      "suggestionId": "TEMP-1",
                      "priority": "P3",
                      "type": "ADVANCEMENT",
                      "category": "WRITING",
                      "subProblemNo": 0,
                      "title": "符号说明表国际标准量纲补齐",
                      "problemOrGap": "当前符号表中仅列出变量名与含义，未标注物理量纲",
                      "diagnosis": "缺少量纲导致公式物理意义审查时缺乏直观依据",
                      "targetLocation": {
                        "physicalPages": [2],
                        "section": "2.2 符号说明",
                        "anchorBlockIds": ["B_NOM_01"]
                      },
                      "actionPlanMarkdown": "#### 1. 现状\\n缺少物理量纲。\\n\\n#### 2. 方案\\n补全国际标准量纲（SI单位）。",
                      "acceptanceCriteria": ["符号表包含变量、含义、国际量纲三列规范三线表"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B_NOM_01"],
                        "reviewFindingIds": [],
                        "knowledgeCitationIds": ["KC-DOC-001"]
                      }
                    }
                  ]
                }
                """;

        String subTaskQ1Json = """
                {
                  "taskId": "SUG_Q1",
                  "executionStatus": "SUCCESS",
                  "suggestions": [
                    {
                      "suggestionId": "TEMP-2",
                      "priority": "P1",
                      "type": "CORRECTION",
                      "category": "MODEL",
                      "subProblemNo": 1,
                      "title": "磨损速率方程非线性修正与边界设定",
                      "problemOrGap": "磨损模型假设人流通行速度恒定，未考虑拥挤峰值阶段的摩擦加速度累积",
                      "diagnosis": "评审指出该假设导致预测寿命偏差达 30% 并扣分",
                      "targetLocation": {
                        "physicalPages": [4],
                        "section": "3.1 机理模型建立",
                        "anchorBlockIds": ["B_EQ_01"]
                      },
                      "actionPlanMarkdown": "#### 1. 原文现状与缺陷\\n公式 (2) 假设速度恒定 $v=v_0$。\\n\\n#### 2. 模型修正方案\\n引入人流密度动力学调整项：\\n$$\\\\frac{dh}{dt} = - k \\\\cdot \\\\rho(t) \\\\cdot v(\\\\rho)^2$$\\n\\n#### 3. 算法实现参考\\n```python\\n# 求解摩擦微分方程\\ndef wear_rate(density, v): return k * density * (v ** 2)\\n```\\n\\n#### 4. 参数设置与推荐图表\\n- 参数推荐：k 磨损系数设为 2.4e-5\\n- 推荐图表：绘制不同人流密度下的台阶凹陷剖面曲面图",
                      "acceptanceCriteria": ["磨损方程能够拟合非对称下沉剖面且 RMSE 小于 0.05"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B_EQ_01"],
                        "reviewFindingIds": ["F_WEAR_001"],
                        "knowledgeCitationIds": ["KC-MECH-002"]
                      }
                    }
                  ]
                }
                """;

        String synthJson = """
                {
                  "workflowVersion": "GROUNDED_SUGGESTION_V3",
                  "overallStrategy": "### 整体修改战略\\n聚焦人流磨损非对称动力学建模，优先修补公式边界条件，并补齐符号量纲与三线表规范。",
                  "topPriorities": [
                    "修正磨损速率方程中的时变人流密度项",
                    "补全符号说明表中所有变量的国际标准量纲"
                  ],
                  "subTaskSummaries": [],
                  "items": [
                    {
                      "suggestionId": "S-1",
                      "priority": "P1",
                      "type": "CORRECTION",
                      "category": "MODEL",
                      "subProblemNo": 1,
                      "title": "磨损速率方程非线性修正与边界设定",
                      "problemOrGap": "磨损模型假设人流通行速度恒定，未考虑拥挤峰值阶段的摩擦加速度累积",
                      "diagnosis": "评审指出该假设导致预测寿命偏差达 30% 并扣分",
                      "targetLocation": {
                        "physicalPages": [4],
                        "section": "3.1 机理模型建立",
                        "anchorBlockIds": ["B_EQ_01"]
                      },
                      "actionPlanMarkdown": "#### 1. 原文现状与缺陷\\n公式 (2) 假设速度恒定 $v=v_0$。\\n\\n#### 2. 模型修正方案\\n引入人流密度动力学调整项：\\n$$\\\\frac{dh}{dt} = - k \\\\cdot \\\\rho(t) \\\\cdot v(\\\\rho)^2$$\\n\\n#### 3. 算法实现参考\\n```python\\n# 求解摩擦微分方程\\ndef wear_rate(density, v): return k * density * (v ** 2)\\n```\\n\\n#### 4. 参数设置与推荐图表\\n- 参数推荐：k 磨损系数设为 2.4e-5\\n- 推荐图表：绘制不同人流密度下的台阶凹陷剖面曲面图",
                      "acceptanceCriteria": ["磨损方程能够拟合非对称下沉剖面且 RMSE 小于 0.05"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B_EQ_01"],
                        "reviewFindingIds": ["F_WEAR_001"],
                        "knowledgeCitationIds": ["KC-MECH-002"]
                      }
                    },
                    {
                      "suggestionId": "S-2",
                      "priority": "P3",
                      "type": "ADVANCEMENT",
                      "category": "WRITING",
                      "subProblemNo": 0,
                      "title": "符号说明表国际标准量纲补齐",
                      "problemOrGap": "当前符号表中仅列出变量名与含义，未标注物理量纲",
                      "diagnosis": "缺少量纲导致公式物理意义审查时缺乏直观依据",
                      "targetLocation": {
                        "physicalPages": [2],
                        "section": "2.2 符号说明",
                        "anchorBlockIds": ["B_NOM_01"]
                      },
                      "actionPlanMarkdown": "#### 1. 现状\\n缺少物理量纲。\\n\\n#### 2. 方案\\n补全国际标准量纲（SI单位）。",
                      "acceptanceCriteria": ["符号表包含变量、含义、国际量纲三列规范三线表"],
                      "evidenceChain": {
                        "paperEvidenceIds": ["B_NOM_01"],
                        "reviewFindingIds": [],
                        "knowledgeCitationIds": ["KC-DOC-001"]
                      }
                    }
                  ]
                }
                """;

        when(aiClient.chat(any())).thenReturn(
                resp(plannerJson),
                resp(subTaskStructJson),
                resp(subTaskQ1Json),
                resp(synthJson)
        );

        when(knowledgeFeignClient.retrieve(any())).thenReturn(Result.ok(new KnowledgeRetrievalResultDTO(
                "run-e2e", "SUGGESTION_DEEP_RETRIEVAL_V1", "VECTOR+BM25_RRF", "idx", "man", "src", "COMPLETED",
                List.of(new KnowledgeCitationDTO("KC-MECH-002", "doc-mech", "chunk-1", "动力学方程建模", "path", null, "hash", "L4", "GENERAL_MODELING", 1.0, "机理分析"))
        )));

        SuggestionTask task = new SuggestionTask();
        task.setId(9901L);
        task.setSubmissionId(101L);
        ProblemContextDTO problem = new ProblemContextDTO();
        problem.setId(51L);
        problem.setTitle("2025 MCM 问题 A：台阶持续磨损");
        problem.setContentMarkdown(problemMarkdown);

        PaperParseDTO parse = new PaperParseDTO();
        parse.setSubmissionId(101L);
        parse.setArtifactId(201L);
        parse.setWorkflowVersion("PAPER_PARSE_V2");
        parse.setStatus("SUCCESS");
        parse.setPageCount(10);
        parse.setDocumentJson("""
                {"sections":[{"sectionId":"SEC_01","title":"1 摘要与引言","headingBlockId":"B_HEAD_01","physicalPage":1},
                             {"sectionId":"SEC_03","title":"3 磨损机理模型","headingBlockId":"B_HEAD_03","physicalPage":4}],
                 "blocks":[{"blockId":"B_NOM_01","type":"TABLE","physicalPage":2,"text":"符号说明表内容"},
                           {"blockId":"B_EQ_01","type":"PARAGRAPH","physicalPage":4,"text":"磨损速率方程公式"}]}
                """);

        ReviewEvidenceSnapshot reviewEvidence = new ReviewEvidenceSnapshot(
                501L, 501L, "DEEP_EVIDENCE_REVIEW_V3", null,
                List.of(new ReviewEvidenceSnapshot.Finding("F_WEAR_001", "ISSUE", "MODEL", "HIGH", "磨损速率假设不当", "-2.0 分", "$.path", List.of("B_EQ_01"))),
                "{\"findings\":[]}");

        // 3. 执行端到端工作流
        SuggestionWorkflowResult result = workflow.execute(task, problem, parse, reviewEvidence);

        // 4. 断言端到端交付产物
        assertThat(result).isNotNull();
        GroundedSuggestionV3Output output = objectMapper.readValue(result.resultJson(), GroundedSuggestionV3Output.class);
        assertThat(output.workflowVersion()).isEqualTo("GROUNDED_SUGGESTION_V3");
        assertThat(output.overallStrategy()).contains("聚焦人流磨损非对称动力学建模");
        assertThat(output.topPriorities()).hasSize(2);
        assertThat(output.subTaskSummaries()).hasSize(2);
        assertThat(output.items()).hasSize(2);

        // 验证改错类建议
        var correction = output.items().get(0);
        assertThat(correction.suggestionId()).isEqualTo("S-1");
        assertThat(correction.priority()).isEqualTo("P1");
        assertThat(correction.type()).isEqualTo("CORRECTION");
        assertThat(correction.actionPlanMarkdown()).contains("\\frac{dh}{dt}");
        assertThat(correction.evidenceChain().reviewFindingIds()).containsExactly("F_WEAR_001");

        // 验证升华类建议
        var advancement = output.items().get(1);
        assertThat(advancement.suggestionId()).isEqualTo("S-2");
        assertThat(advancement.priority()).isEqualTo("P3");
        assertThat(advancement.type()).isEqualTo("ADVANCEMENT");
        assertThat(advancement.evidenceChain().reviewFindingIds()).isEmpty();
        assertThat(advancement.evidenceChain().paperEvidenceIds()).containsExactly("B_NOM_01");
    }

    private AiChatResponse resp(String json) {
        return new AiChatResponse("call-e2e", AiProvider.NEW_API, "gemini-3.8-flash-high", "resp-e2e", json, null, "stop", null);
    }
}
