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
import com.leetmodel.common.api.dto.SubProblemCategoryDTO;
import com.leetmodel.common.api.dto.SubTaskPlanDTO;
import com.leetmodel.common.api.dto.TaskPlanResultDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 阶段二动态任务规划算子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskPlannerOperator {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final SubProblemClassifier classifier;

    public TaskPlanResultDTO plan(ReviewTask task, ProblemContextDTO problem, PaperDocumentV2 document) {
        List<SubProblemCategoryDTO> categories = classifier.parseAndClassifyQuestions(
                problem == null ? "" : problem.getContentMarkdown());

        boolean v4 = "DEEP_EVIDENCE_REVIEW_V4".equals(task.getWorkflowVersion());
        String systemPrompt = PromptTemplateRenderer.loadClasspathPrompt(
                v4 ? "prompts/phase2-task-planner-v4.st"
                        : "prompts/phase2-task-planner.st"
        );
        String userPrompt = buildPlannerUserPrompt(categories, document);

        String taskKey = task.getId() == null
                ? "experiment:" + (task.getExperimentRunId() == null ? UUID.randomUUID() : task.getExperimentRunId())
                : "task:" + task.getId();

        AiCallContext context = new AiCallContext(
                "ai-review-service",
                AiFeatureCode.PAPER_REVIEW,
                task.getId() == null ? AiOperationCode.EXPERIMENT_REVIEW : AiOperationCode.FORMAL_REVIEW,
                taskKey,
                v4 ? "DEEP_EVIDENCE_REVIEW_V4" : "DEEP_EVIDENCE_REVIEW_V3",
                v4 ? "PROMPT_PHASE2_PLANNER_0002" : "PROMPT_PHASE2_PLANNER_0001",
                task.getModelExecutionConfigVersion() == null
                        ? (v4 ? "MODEL_CFG_REVIEW_TEXT_0004"
                        : DeepEvidenceReviewV3Workflow.MODEL_EXECUTION_CONFIG_VERSION)
                        : task.getModelExecutionConfigVersion(),
                task.getEvaluationTaskId(),
                task.getId() == null ? AiCallPriority.P3 : AiCallPriority.P1,
                "planner:" + taskKey + ":attempt:" + task.getAttemptNo(),
                Instant.now().plusSeconds(180)
        );

        AiChatRequest request = new AiChatRequest(
                AiModality.TEXT,
                context,
                List.of(
                        new AiMessage(AiRole.SYSTEM, List.of(new AiContentPart(AiContentType.TEXT, systemPrompt, null))),
                        new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))
                ),
                DeepEvidenceReviewV3Workflow.MAX_OUTPUT_TOKENS,
                DeepEvidenceReviewV3Workflow.TEMPERATURE,
                AiResponseFormat.JSON_OBJECT,
                false
        );

        AiChatResponse response = aiClient.chat(request);
        if (response == null || response.content() == null || response.content().isBlank()) {
            log.warn("规划算子 AI 响应为空，使用启发式规划保底");
            return fallbackPlan(categories, document);
        }

        try {
            TaskPlanResultDTO rawResult = V3OutputParser.parse(
                    objectMapper, response.content(), TaskPlanResultDTO.class);
            return reconcilePlan(rawResult, categories, document);
        } catch (Exception exception) {
            log.warn("任务规划算子输出解析失败，使用启发式规划保底: {}", exception.getMessage());
            return fallbackPlan(categories, document);
        }
    }

    private String buildPlannerUserPrompt(List<SubProblemCategoryDTO> categories, PaperDocumentV2 document) {
        StringBuilder sb = new StringBuilder();
        sb.append("【赛题小问清单与判定题型】\n");
        for (var c : categories) {
            sb.append(String.format("- 问题 %d: [%s] 典型方法: %s\n",
                    c.getQuestionNo(), c.getCategoryName(),
                    c.getTypicalMethods() != null ? String.join(",", c.getTypicalMethods()) : ""));
        }
        sb.append("\n【论文目录大纲 (SectionIndex)】\n");
        if (document.sections() != null) {
            for (var s : document.sections()) {
                sb.append(String.format("- id: %s | 标题: %s | 页码: %d | 块: %s\n",
                        s.sectionId(), s.title(), s.physicalPage(), s.headingBlockId()));
            }
        } else {
            sb.append("（无显式目录大纲）\n");
        }
        return sb.toString();
    }

    private TaskPlanResultDTO reconcilePlan(
            TaskPlanResultDTO raw, List<SubProblemCategoryDTO> categories, PaperDocumentV2 document) {
        if (raw == null || raw.getTasks() == null || raw.getTasks().isEmpty()) {
            return fallbackPlan(categories, document);
        }
        List<SubTaskPlanDTO> reconciledTasks = new ArrayList<>();
        boolean hasAbstract = false;
        boolean hasSensitivity = false;
        Set<Integer> coveredQuestions = new HashSet<>();

        Map<String, SubProblemCategoryDTO> categoryByCode = new HashMap<>();
        categories.forEach(c -> categoryByCode.put(c.getCategoryCode(), c));
        Map<Integer, SubProblemCategoryDTO> categoryByQNo = new HashMap<>();
        categories.forEach(c -> categoryByQNo.put(c.getQuestionNo(), c));

        for (var t : raw.getTasks()) {
            if ("ABSTRACT_VERIFICATION".equals(t.getTaskType())) {
                hasAbstract = true;
                t.setTaskId("TASK_ABSTRACT_VERIFY");
                t.setTargetQuestionNo(0);
                t.setSubProblemCategory(classifier.classify(0, "摘要核验"));
                reconciledTasks.add(enrichAnchors(t, document));
            } else if ("SENSITIVITY_EVALUATION".equals(t.getTaskType())) {
                hasSensitivity = true;
                t.setTaskId("TASK_SENSITIVITY_EVAL");
                t.setTargetQuestionNo(0);
                t.setSubProblemCategory(classifier.classify(0, "灵敏度分析"));
                reconciledTasks.add(enrichAnchors(t, document));
            } else if ("SUB_PROBLEM_EVALUATION".equals(t.getTaskType())) {
                int qNo = t.getTargetQuestionNo() == null ? 1 : t.getTargetQuestionNo();
                coveredQuestions.add(qNo);
                t.setTaskId("TASK_Q" + qNo + "_EVAL");
                SubProblemCategoryDTO cat = categoryByQNo.getOrDefault(qNo, classifier.classify(qNo, t.getTaskName()));
                t.setSubProblemCategory(cat);
                reconciledTasks.add(enrichAnchors(t, document));
            }
        }

        // 保证摘要任务存在
        if (!hasAbstract) {
            reconciledTasks.add(0, SubTaskPlanDTO.builder()
                    .taskId("TASK_ABSTRACT_VERIFY")
                    .taskType("ABSTRACT_VERIFICATION")
                    .taskName("摘要全文自洽性对照核验")
                    .targetQuestionNo(0)
                    .subProblemCategory(classifier.classify(0, "摘要"))
                    .evaluationObjectives(List.of("核验摘要方法与正文一致性", "核验摘要量化指标真实性"))
                    .build());
        }

        // 补齐遗漏的小题
        for (var cat : categories) {
            if (!coveredQuestions.contains(cat.getQuestionNo())) {
                SubTaskPlanDTO taskDto = SubTaskPlanDTO.builder()
                        .taskId("TASK_Q" + cat.getQuestionNo() + "_EVAL")
                        .taskType("SUB_PROBLEM_EVALUATION")
                        .taskName("问题" + cat.getQuestionNo() + "模型建立与求解")
                        .targetQuestionNo(cat.getQuestionNo())
                        .subProblemCategory(cat)
                        .evaluationObjectives(cat.getFocusAspects())
                        .build();
                reconciledTasks.add(enrichAnchors(taskDto, document));
            }
        }

        // 保证灵敏度任务存在
        if (!hasSensitivity) {
            reconciledTasks.add(SubTaskPlanDTO.builder()
                    .taskId("TASK_SENSITIVITY_EVAL")
                    .taskType("SENSITIVITY_EVALUATION")
                    .taskName("全局灵敏度与鲁棒性检验")
                    .targetQuestionNo(0)
                    .subProblemCategory(classifier.classify(0, "灵敏度分析"))
                    .evaluationObjectives(List.of("参数扰动分析充分性", "管理建议现实指导性"))
                    .build());
        }

        return TaskPlanResultDTO.builder()
                .totalQuestions(categories.size())
                .tasks(reconciledTasks)
                .plannerModelVersion("PLANNER_HYBRID_V3")
                .build();
    }

    private SubTaskPlanDTO enrichAnchors(SubTaskPlanDTO taskDto, PaperDocumentV2 document) {
        if (document.sections() == null || document.sections().isEmpty()) {
            return taskDto;
        }
        List<SubTaskPlanDTO.SectionAnchorDTO> anchors = new ArrayList<>();
        Set<String> selectedSectionIds = new HashSet<>();

        // 优先采用规划模型返回的明确章节 ID，并由服务端校验它们确实存在。
        if (taskDto.getSuggestedSectionIds() != null) {
            selectedSectionIds.addAll(taskDto.getSuggestedSectionIds());
        }
        for (int i = 0; i < document.sections().size(); i++) {
            var section = document.sections().get(i);
            if (selectedSectionIds.contains(section.sectionId())) {
                anchors.add(toSectionAnchor(document, i, section));
            }
        }
        if (!anchors.isEmpty()) {
            taskDto.setSuggestedSectionAnchors(anchors);
            return taskDto;
        }

        int qNo = taskDto.getTargetQuestionNo() == null ? 0 : taskDto.getTargetQuestionNo();

        for (int i = 0; i < document.sections().size(); i++) {
            var sec = document.sections().get(i);
            boolean match = matchesTaskSection(taskDto.getTaskType(), qNo, sec.title());
            if (match) {
                anchors.add(toSectionAnchor(document, i, sec));
            }
        }
        taskDto.setSuggestedSectionAnchors(anchors);
        return taskDto;
    }

    private boolean matchesTaskSection(String taskType, int questionNo, String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.toLowerCase(java.util.Locale.ROOT);
        if ("ABSTRACT_VERIFICATION".equals(taskType)) {
            return title.contains("摘要")
                    || title.contains("summary")
                    || title.contains("abstract");
        }
        if ("SENSITIVITY_EVALUATION".equals(taskType)) {
            return title.contains("灵敏度")
                    || title.contains("检验")
                    || title.contains("评价")
                    || title.contains("sensitivity")
                    || title.contains("robust")
                    || title.contains("model evaluation")
                    || title.contains("strength and weakness");
        }
        if (!"SUB_PROBLEM_EVALUATION".equals(taskType) || questionNo <= 0) {
            return false;
        }
        return title.contains("问题" + questionNo)
                || title.contains(chineseQuestionLabel(questionNo))
                || title.contains("problem " + questionNo)
                || title.contains("solution of problem " + questionNo);
    }

    private SubTaskPlanDTO.SectionAnchorDTO toSectionAnchor(
            PaperDocumentV2 document,
            int sectionIndex,
            PaperDocumentV2.SectionIndex section
    ) {
        String endBlockId = sectionIndex + 1 < document.sections().size()
                ? document.sections().get(sectionIndex + 1).headingBlockId()
                : null;
        return SubTaskPlanDTO.SectionAnchorDTO.builder()
                .sectionId(section.sectionId())
                .title(section.title())
                .startBlockId(section.headingBlockId())
                .endBlockId(endBlockId)
                .physicalPage(section.physicalPage())
                .matchConfidence(1.0)
                .build();
    }

    private String chineseQuestionLabel(int questionNo) {
        return switch (questionNo) {
            case 1 -> "问题一";
            case 2 -> "问题二";
            case 3 -> "问题三";
            case 4 -> "问题四";
            case 5 -> "问题五";
            default -> "问题" + questionNo;
        };
    }

    private TaskPlanResultDTO fallbackPlan(List<SubProblemCategoryDTO> categories, PaperDocumentV2 document) {
        List<SubTaskPlanDTO> tasks = new ArrayList<>();
        tasks.add(SubTaskPlanDTO.builder()
                .taskId("TASK_ABSTRACT_VERIFY")
                .taskType("ABSTRACT_VERIFICATION")
                .taskName("摘要全文自洽性对照核验")
                .targetQuestionNo(0)
                .subProblemCategory(classifier.classify(0, "摘要"))
                .evaluationObjectives(List.of("核验摘要方法与正文一致性", "核验摘要量化指标真实性"))
                .build());

        for (var cat : categories) {
            var task = SubTaskPlanDTO.builder()
                    .taskId("TASK_Q" + cat.getQuestionNo() + "_EVAL")
                    .taskType("SUB_PROBLEM_EVALUATION")
                    .taskName("问题" + cat.getQuestionNo() + "模型建立与求解")
                    .targetQuestionNo(cat.getQuestionNo())
                    .subProblemCategory(cat)
                    .evaluationObjectives(cat.getFocusAspects())
                    .build();
            tasks.add(enrichAnchors(task, document));
        }

        tasks.add(SubTaskPlanDTO.builder()
                .taskId("TASK_SENSITIVITY_EVAL")
                .taskType("SENSITIVITY_EVALUATION")
                .taskName("全局灵敏度与鲁棒性检验")
                .targetQuestionNo(0)
                .subProblemCategory(classifier.classify(0, "灵敏度分析"))
                .evaluationObjectives(List.of("参数扰动分析充分性", "管理建议现实指导性"))
                .build());

        return TaskPlanResultDTO.builder()
                .totalQuestions(categories.size())
                .tasks(tasks)
                .plannerModelVersion("PLANNER_FALLBACK_HEURISTIC")
                .build();
    }
}
