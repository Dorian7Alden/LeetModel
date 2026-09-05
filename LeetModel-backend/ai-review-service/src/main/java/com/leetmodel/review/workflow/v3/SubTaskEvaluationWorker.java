package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;
import com.leetmodel.common.api.dto.SubTaskPlanDTO;
import com.leetmodel.common.api.dto.TaskAssembledContextDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 阶段二单任务并发评估 Worker 算子。
 * 负责单个子任务的提示词装配、模型调用与结果防御性解析，具备局部降级与容错能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubTaskEvaluationWorker {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final ContextSlicingEngine slicingEngine;

    public SubTaskEvaluationResultDTO execute(
            ReviewTask task, SubTaskPlanDTO plan, PaperDocumentV2 document, ProblemContextDTO problem) {
        try {
            TaskAssembledContextDTO assembled = slicingEngine.assembleContext(document, plan, problem);
            String promptTemplate = selectPromptTemplate(plan.getTaskType());
            Map<String, String> variables = buildVariables(assembled);
            String renderedPrompt = PromptTemplateRenderer.render(promptTemplate, variables);

            String taskKey = (task.getId() == null
                    ? "experiment:" + (task.getExperimentRunId() == null ? UUID.randomUUID() : task.getExperimentRunId())
                    : "task:" + task.getId()) + ":" + plan.getTaskId();

            AiCallContext context = new AiCallContext(
                    "ai-review-service",
                    AiFeatureCode.PAPER_REVIEW,
                    task.getId() == null ? AiOperationCode.EXPERIMENT_REVIEW : AiOperationCode.FORMAL_REVIEW,
                    taskKey,
                    "DEEP_EVIDENCE_REVIEW_V3",
                    "PROMPT_SUBTASK_" + plan.getTaskType() + "_0001",
                    task.getModelExecutionConfigVersion() == null ? "MODEL_CFG_REVIEW_TEXT_0002" : task.getModelExecutionConfigVersion(),
                    task.getEvaluationTaskId(),
                    task.getId() == null ? AiCallPriority.P3 : AiCallPriority.P1,
                    "subtask:" + taskKey + ":attempt:" + task.getAttemptNo(),
                    Instant.now().plusSeconds(180)
            );

            AiChatRequest request = new AiChatRequest(
                    AiModality.TEXT,
                    context,
                    List.of(
                            new AiMessage(AiRole.SYSTEM, List.of(new AiContentPart(AiContentType.TEXT, renderedPrompt, null))),
                            new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, "请严格根据上述背景与正文切片，输出符合要求的 JSON 结构化评审结果：", null)))
                    ),
                    4096,
                    0.1,
                    AiResponseFormat.JSON_OBJECT,
                    false
            );

            AiChatResponse response = aiClient.chat(request);
            if (response == null || response.content() == null || response.content().isBlank()) {
                log.warn("子任务调用未返回内容，启动降级容错: taskId={}", plan.getTaskId());
                return fallbackDegradedResult(plan, "模型响应为空，启动容错保护");
            }

            SubTaskEvaluationResultDTO result = V3OutputParser.parse(
                    objectMapper, response.content(), SubTaskEvaluationResultDTO.class);
            return sanitizeResult(result, plan);

        } catch (Exception exception) {
            log.warn("子任务执行失败，启动局部降级隔离: taskId={}, error={}",
                    plan.getTaskId(), exception.getMessage());
            return fallbackDegradedResult(plan, exception.getMessage());
        }
    }

    private String selectPromptTemplate(String taskType) {
        return switch (taskType) {
            case "ABSTRACT_VERIFICATION" -> PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-abstract-verification.st");
            case "SENSITIVITY_EVALUATION" -> PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-sensitivity-evaluation.st");
            default -> PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-subtask-evaluation.st");
        };
    }

    private Map<String, String> buildVariables(TaskAssembledContextDTO assembled) {
        Map<String, String> vars = new HashMap<>();
        var plan = assembled.getTaskPlan();
        vars.put("taskId", plan.getTaskId());
        vars.put("taskName", plan.getTaskName());
        vars.put("questionNo", String.valueOf(plan.getTargetQuestionNo()));
        vars.put("categoryName", plan.getSubProblemCategory() != null ? plan.getSubProblemCategory().getCategoryName() : "通用建模类");
        vars.put("problemQuestionMarkdown", assembled.getProblemQuestionMarkdown() != null ? assembled.getProblemQuestionMarkdown() : "（无特定题面）");

        // 外部 RAG 引用
        StringBuilder ragSb = new StringBuilder();
        if (assembled.getKnowledgeCitations() != null) {
            for (var c : assembled.getKnowledgeCitations()) {
                ragSb.append(String.format("### 规则参考: %s (权威等级: %s)\n%s\n\n", c.getTitle(), c.getAuthorityLevel(), c.getContent()));
            }
        }
        vars.put("knowledgeCitationsMarkdown", ragSb.toString().trim());

        // 前置假设
        StringBuilder assumeSb = new StringBuilder();
        if (assembled.getAttachedAssumptions() != null) {
            for (var b : assembled.getAttachedAssumptions()) {
                assumeSb.append(b.getText()).append("\n");
            }
        }
        vars.put("attachedAssumptions", assumeSb.toString().trim());

        // 前置符号
        StringBuilder nomenSb = new StringBuilder();
        if (assembled.getAttachedNomenclature() != null) {
            for (var b : assembled.getAttachedNomenclature()) {
                if (b.getHtmlTable() != null) {
                    nomenSb.append("[符号表HTML]\n").append(b.getHtmlTable()).append("\n");
                } else if (b.getText() != null) {
                    nomenSb.append(b.getText()).append("\n");
                }
            }
        }
        vars.put("attachedNomenclature", nomenSb.toString().trim());

        // 目标切片 Blocks
        StringBuilder targetSb = new StringBuilder();
        if (assembled.getTargetSectionBlocks() != null) {
            for (var b : assembled.getTargetSectionBlocks()) {
                targetSb.append(String.format("[Block: %s, 页码: %d, 类型: %s]\n", b.getBlockId(), b.getPhysicalPage(), b.getBlockType()));
                if (b.getLatex() != null) {
                    targetSb.append("$$").append(b.getLatex()).append("$$\n");
                } else if (b.getHtmlTable() != null) {
                    targetSb.append(b.getHtmlTable()).append("\n");
                } else if (b.getCodeContent() != null) {
                    targetSb.append("```").append(b.getCodeLanguage() != null ? b.getCodeLanguage() : "").append("\n")
                            .append(b.getCodeContent()).append("\n```\n");
                } else if (b.getFigureDescription() != null) {
                    targetSb.append("[插图描述: ").append(b.getFigureDescription()).append("]\n");
                } else if (b.getText() != null) {
                    targetSb.append(b.getText()).append("\n");
                }
                targetSb.append("\n");
            }
        }
        String targetContent = targetSb.toString().trim();
        vars.put("targetSectionBlocksWithLatexAndHtml", targetContent);
        vars.put("abstractContent", targetContent);
        vars.put("paperResultsSummaryTablesAndConclusions", targetContent);
        vars.put("sensitivityBlocksWithFiguresAndTables", targetContent);
        return vars;
    }

    private SubTaskEvaluationResultDTO sanitizeResult(SubTaskEvaluationResultDTO raw, SubTaskPlanDTO plan) {
        BigDecimal maxScore = determineMaxScore(plan.getTaskType());
        raw.setTaskId(plan.getTaskId());
        raw.setTaskType(plan.getTaskType());
        raw.setTargetQuestionNo(plan.getTargetQuestionNo());
        raw.setExecutionStatus("SUCCESS");
        raw.setMaxScore(maxScore);

        BigDecimal score = raw.getScore();
        if (score == null) score = maxScore.multiply(BigDecimal.valueOf(0.8));
        if (score.compareTo(maxScore) > 0) score = maxScore;
        if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
        raw.setScore(score.setScale(1, RoundingMode.HALF_UP));
        return raw;
    }

    private BigDecimal determineMaxScore(String taskType) {
        return switch (taskType) {
            case "ABSTRACT_VERIFICATION" -> BigDecimal.valueOf(10.0);
            case "SENSITIVITY_EVALUATION" -> BigDecimal.valueOf(15.0);
            default -> BigDecimal.valueOf(15.0);
        };
    }

    private SubTaskEvaluationResultDTO fallbackDegradedResult(SubTaskPlanDTO plan, String reason) {
        BigDecimal maxScore = determineMaxScore(plan.getTaskType());
        BigDecimal degradedScore = maxScore.multiply(BigDecimal.valueOf(0.6)).setScale(1, RoundingMode.HALF_UP);

        return SubTaskEvaluationResultDTO.builder()
                .taskId(plan.getTaskId())
                .taskType(plan.getTaskType())
                .targetQuestionNo(plan.getTargetQuestionNo())
                .executionStatus("DEGRADED")
                .maxScore(maxScore)
                .score(degradedScore)
                .evaluationSummary("由于该章节复杂推导在当前调用中触发容错保底，系统赋予基准保底分并标记复核。原因: " + reason)
                .aspectScores(List.of(
                        SubTaskEvaluationResultDTO.SubTaskAspectScoreDTO.builder()
                                .aspectCode("FALLBACK")
                                .aspectName("容错保底分项")
                                .maxScore(maxScore)
                                .score(degradedScore)
                                .deductionReason("容错保底机制介入")
                                .build()
                ))
                .observations(List.of())
                .findings(List.of(
                        SubTaskEvaluationResultDTO.SubTaskFindingDTO.builder()
                                .findingId("F_" + plan.getTaskId() + "_DEGRADED")
                                .taskId(plan.getTaskId())
                                .questionNo(plan.getTargetQuestionNo())
                                .type("ISSUE")
                                .severity("LOW")
                                .statement("该小题执行触发容错保底，评分基于保守中位数核定。")
                                .scoreImpact("-0.0 分")
                                .build()
                ))
                .build();
    }
}
