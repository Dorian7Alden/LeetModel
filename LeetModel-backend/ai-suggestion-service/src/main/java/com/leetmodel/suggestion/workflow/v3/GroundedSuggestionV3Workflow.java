package com.leetmodel.suggestion.workflow.v3;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 第三代动态证据化论文建议工作流（GROUNDED_SUGGESTION_V3）。
 * 具备动态任务规划、按需精准 RAG、局部切片装配、子任务并发推演、汇总 AI 排序与双轨依据链。
 */
@Slf4j
@Component
public class GroundedSuggestionV3Workflow {
    public static final String VERSION = "GROUNDED_SUGGESTION_V3";
    public static final String RESULT_SCHEMA_VERSION = "GROUNDED_SUGGESTION_V3";
    private static final Map<String, Integer> PRIORITIES = Map.of("P0", 0, "P1", 1, "P2", 2, "P3", 3);

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeRetrievalFeignClient knowledgeFeignClient;
    private final Executor subTaskExecutor;

    private final String plannerPromptTemplate;
    private final String subTaskPromptTemplate;
    private final String synthesizerPromptTemplate;

    public GroundedSuggestionV3Workflow(
            AiClient aiClient,
            ObjectMapper objectMapper,
            @Autowired(required = false) KnowledgeRetrievalFeignClient knowledgeFeignClient,
            @Qualifier("suggestionSubTaskExecutor") Executor subTaskExecutor
    ) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.knowledgeFeignClient = knowledgeFeignClient;
        this.subTaskExecutor = subTaskExecutor;
        this.plannerPromptTemplate = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-suggestion-planner.st");
        this.subTaskPromptTemplate = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-subtask-suggestion.st");
        this.synthesizerPromptTemplate = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase3-suggestion-synthesizer.st");
    }

    public String currentPrompt() {
        return synthesizerPromptTemplate;
    }

    public SuggestionWorkflowResult execute(
            SuggestionTask task,
            ProblemContextDTO problem,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence
    ) throws Exception {
        log.info("开始执行建议V3工作流: taskId={}, submissionId={}", task.getId(), task.getSubmissionId());

        // 1. 阶段一：执行建议任务规划算子
        SuggestionPlannerOutput plannerOutput = planTasks(task, problem, parse, reviewEvidence);
        List<SuggestionPlannerOutput.PlannerTask> plannedTasks = plannerOutput.tasks();
        if (plannedTasks == null || plannedTasks.isEmpty()) {
            throw new IllegalStateException("建议规划算子未能生成有效任务列表");
        }

        // 2. 阶段二：并发执行子任务推演 (结合局部切片与按需精准 RAG)
        List<CompletableFuture<SubTaskExecutionOutcome>> futures = new ArrayList<>();
        for (var subTask : plannedTasks) {
            futures.add(CompletableFuture.supplyAsync(() -> executeSubTask(task, subTask, problem, parse, reviewEvidence), subTaskExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(120, TimeUnit.SECONDS);

        List<SubTaskExecutionOutcome> outcomes = new ArrayList<>();
        List<GroundedSuggestionV3Output.SubTaskSummary> summaries = new ArrayList<>();
        List<GroundedSuggestionV3Output.Item> candidateItems = new ArrayList<>();

        for (var future : futures) {
            try {
                SubTaskExecutionOutcome outcome = future.get();
                outcomes.add(outcome);
                summaries.add(outcome.summary);
                if (outcome.items != null) {
                    candidateItems.addAll(outcome.items);
                }
            } catch (Exception e) {
                log.warn("子任务获取结果异常: {}", e.getMessage());
            }
        }

        // 3. 阶段三：调用汇总与优先级统筹 AI
        GroundedSuggestionV3Output finalOutput = synthesizeOutput(task, problem, reviewEvidence, summaries, candidateItems);

        // 4. 排序与自增 ID 规范化
        finalOutput = normalizeAndSort(finalOutput, summaries);

        String resultJson = objectMapper.writeValueAsString(finalOutput);
        return new SuggestionWorkflowResult(resultJson, "gemini-3.8-flash-high", "call-sug-v3-" + task.getId());
    }

    private SuggestionPlannerOutput planTasks(
            SuggestionTask task,
            ProblemContextDTO problem,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence
    ) {
        try {
            String sectionIndexJson = extractSectionIndex(parse.getDocumentJson());
            Map<String, String> variables = Map.of(
                    "problemQuestionsList", limit(problem.getContentMarkdown(), 20000),
                    "paperSectionIndex", sectionIndexJson,
                    "reviewDeductionsList", limit(reviewEvidence.snapshotJson(), 30000)
            );
            String userPrompt = PromptTemplateRenderer.render(plannerPromptTemplate, variables);
            String callId = "sug:planner:" + task.getId();
            AiCallContext context = new AiCallContext("ai-suggestion-service",
                    AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                    callId, VERSION, "PROMPT_PLANNER_0001",
                    "MODEL_CFG_SUGGESTION_TEXT_0002", null, AiCallPriority.P1,
                    callId, Instant.now().plusSeconds(180));
            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))),
                    4096, 0.1, AiResponseFormat.JSON_OBJECT, false));

            if (response != null && response.content() != null && !response.content().isBlank()) {
                return V3OutputParser.parse(objectMapper, response.content(), SuggestionPlannerOutput.class);
            }
        } catch (Exception e) {
            log.warn("建议规划算子调用异常，采用默认拆解: {}", e.getMessage());
        }
        // 默认兜底拆解
        return new SuggestionPlannerOutput(List.of(
                new SuggestionPlannerOutput.PlannerTask("SUG_TASK_STRUCT", "STRUCTURAL_SUGGESTION", "全篇结构规范与摘要数值闭环建议", 0, "WRITING", List.of(), List.of("自查摘要要素与符号量纲")),
                new SuggestionPlannerOutput.PlannerTask("SUG_TASK_Q1", "SUB_PROBLEM_SUGGESTION", "问题一数学建模推导与算法优化建议", 1, "OPTIMIZATION", List.of(), List.of("优化模型表达式与对比基准")),
                new SuggestionPlannerOutput.PlannerTask("SUG_TASK_SENS", "SENSITIVITY_SUGGESTION", "模型检验与敏感性分析专项建议", 0, "VALIDATION", List.of(), List.of("设计敏感性正交扰动试验"))
        ));
    }

    private SubTaskExecutionOutcome executeSubTask(
            SuggestionTask task,
            SuggestionPlannerOutput.PlannerTask subTask,
            ProblemContextDTO problem,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence
    ) {
        String taskId = subTask.taskId();
        try {
            // 1. 按需精准 RAG 检索
            String ragContent = retrieveTargetedKnowledge(subTask, problem);

            // 2. 局部正文切片与假设符号提取
            String targetSectionSlice = extractTargetSlice(parse.getDocumentJson(), subTask.suggestedSectionIds(), subTask.targetQuestionNo());
            String assumptionsAndNomenclature = extractAssumptionsAndNomenclature(parse.getDocumentJson());
            String relatedFindings = extractRelatedFindings(reviewEvidence, subTask.targetQuestionNo());

            // 3. 装配提示词
            Map<String, String> variables = new HashMap<>();
            variables.put("taskName", subTask.taskName());
            variables.put("questionNo", String.valueOf(subTask.targetQuestionNo() == null ? 0 : subTask.targetQuestionNo()));
            variables.put("categoryName", subTask.categoryCode() == null ? "通用建模" : subTask.categoryCode());
            variables.put("taskId", subTask.taskId());
            variables.put("problemQuestionMarkdown", limit(problem.getContentMarkdown(), 10000));
            variables.put("attachedAssumptionsAndNomenclature", assumptionsAndNomenclature);
            variables.put("targetSectionBlocksWithLatexAndHtml", targetSectionSlice);
            variables.put("relatedReviewFindings", relatedFindings);
            variables.put("knowledgeCitationsMarkdown", ragContent);

            String userPrompt = PromptTemplateRenderer.render(subTaskPromptTemplate, variables);
            String callId = "sug:subtask:" + task.getId() + ":" + taskId;
            AiCallContext context = new AiCallContext("ai-suggestion-service",
                    AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                    callId, VERSION, "PROMPT_SUBTASK_0001",
                    "MODEL_CFG_SUGGESTION_TEXT_0002", null, AiCallPriority.P1,
                    callId, Instant.now().plusSeconds(180));

            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))),
                    4096, 0.15, AiResponseFormat.JSON_OBJECT, false));

            if (response != null && response.content() != null && !response.content().isBlank()) {
                SubTaskSuggestionOutput output = V3OutputParser.parse(objectMapper, response.content(), SubTaskSuggestionOutput.class);
                List<GroundedSuggestionV3Output.Item> items = output.suggestions() != null ? output.suggestions() : List.of();
                var summary = new GroundedSuggestionV3Output.SubTaskSummary(
                        taskId, subTask.taskType(), subTask.taskName(), "SUCCESS", items.size());
                return new SubTaskExecutionOutcome(summary, items);
            }
        } catch (Exception e) {
            log.warn("子任务推演执行异常，降级隔离: taskId={}, error={}", taskId, e.getMessage());
        }
        var degradedSummary = new GroundedSuggestionV3Output.SubTaskSummary(
                taskId, subTask.taskType(), subTask.taskName(), "DEGRADED", 0);
        return new SubTaskExecutionOutcome(degradedSummary, List.of());
    }

    private GroundedSuggestionV3Output synthesizeOutput(
            SuggestionTask task,
            ProblemContextDTO problem,
            ReviewEvidenceSnapshot reviewEvidence,
            List<GroundedSuggestionV3Output.SubTaskSummary> summaries,
            List<GroundedSuggestionV3Output.Item> candidateItems
    ) {
        try {
            Map<String, String> variables = Map.of(
                    "problemSummary", problem.getTitle() + "\n" + limit(problem.getContentMarkdown(), 3000),
                    "reviewOverallSnapshot", limit(reviewEvidence.snapshotJson(), 20000),
                    "allSubTaskSuggestionsJson", objectMapper.writeValueAsString(candidateItems)
            );
            String userPrompt = PromptTemplateRenderer.render(synthesizerPromptTemplate, variables);
            String callId = "sug:synth:" + task.getId();
            AiCallContext context = new AiCallContext("ai-suggestion-service",
                    AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                    callId, VERSION, "PROMPT_SYNTHESIZER_0001",
                    "MODEL_CFG_SUGGESTION_TEXT_0002", null, AiCallPriority.P1,
                    callId, Instant.now().plusSeconds(180));
            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))),
                    6000, 0.15, AiResponseFormat.JSON_OBJECT, false));

            if (response != null && response.content() != null && !response.content().isBlank()) {
                return V3OutputParser.parse(objectMapper, response.content(), GroundedSuggestionV3Output.class);
            }
        } catch (Exception e) {
            log.warn("汇总 AI 统筹异常，采用本地聚合兜底: {}", e.getMessage());
        }
        // 本地兜底汇总
        return new GroundedSuggestionV3Output(
                VERSION,
                "### 整体修改战略指引\n全面覆盖赛题设问，优先修复核心建模约束与变量量纲，并补充基准模型对比。",
                List.of("修复核心数学模型约束", "完善符号说明表国际标准量纲", "补充基准模型与敏感性检验"),
                summaries,
                candidateItems
        );
    }

    private GroundedSuggestionV3Output normalizeAndSort(
            GroundedSuggestionV3Output output,
            List<GroundedSuggestionV3Output.SubTaskSummary> summaries
    ) {
        List<GroundedSuggestionV3Output.Item> rawItems = output.items() != null ? output.items() : List.of();
        List<GroundedSuggestionV3Output.Item> sorted = new ArrayList<>(rawItems);
        sorted.sort(Comparator.comparingInt(item -> PRIORITIES.getOrDefault(item.priority(), 2)));

        List<GroundedSuggestionV3Output.Item> normalized = new ArrayList<>();
        int id = 1;
        for (var item : sorted) {
            normalized.add(new GroundedSuggestionV3Output.Item(
                    "S-" + (id++),
                    item.priority() == null ? "P2" : item.priority(),
                    item.type() == null ? "CORRECTION" : item.type(),
                    item.category() == null ? "MODEL" : item.category(),
                    item.subProblemNo(),
                    item.title() == null ? "修改建议" : item.title(),
                    item.problemOrGap() == null ? "" : item.problemOrGap(),
                    item.diagnosis(),
                    item.targetLocation(),
                    item.actionPlanMarkdown() == null ? "请根据指导建议进行修改" : item.actionPlanMarkdown(),
                    item.acceptanceCriteria() == null || item.acceptanceCriteria().isEmpty() ? List.of("完成模型修正") : item.acceptanceCriteria(),
                    item.evidenceChain()
            ));
        }
        return new GroundedSuggestionV3Output(VERSION, output.overallStrategy(), output.topPriorities(), summaries, normalized);
    }

    private String retrieveTargetedKnowledge(SuggestionPlannerOutput.PlannerTask subTask, ProblemContextDTO problem) {
        if (knowledgeFeignClient == null) return "（无外部知识检索依赖）";
        try {
            String query = (subTask.categoryCode() != null ? subTask.categoryCode() + ": " : "")
                    + problem.getTitle() + " " + String.join(" ", subTask.suggestionObjectives() != null ? subTask.suggestionObjectives() : List.of());
            KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
            request.setWorkflowVersion("SUGGESTION_DEEP_RETRIEVAL_V1");
            request.setScene("PAPER_SUGGESTION_DEEP");
            request.setQuery(limit(query, 500));
            request.setTopK(4);
            request.setTokenBudget(2500);

            Result<KnowledgeRetrievalResultDTO> res = knowledgeFeignClient.retrieve(request);
            if (res != null && res.isSuccess() && res.getData() != null && res.getData().getCitations() != null) {
                StringBuilder sb = new StringBuilder();
                for (var cit : res.getData().getCitations()) {
                    sb.append("【").append(cit.getCitationId()).append("】").append(cit.getTitle())
                            .append(" (").append(cit.getAuthorityLevel()).append("):\n")
                            .append(limit(cit.getContent(), 600)).append("\n\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.debug("按需知识检索降级: {}", e.getMessage());
        }
        return "（未检索到特定参考知识）";
    }

    private String extractSectionIndex(String docJson) {
        if (docJson == null || docJson.isBlank()) return "[]";
        try {
            JsonNode root = objectMapper.readTree(docJson);
            JsonNode sections = root.path("sections");
            return sections.isMissingNode() ? "[]" : objectMapper.writeValueAsString(sections);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String extractTargetSlice(String docJson, List<String> sectionIds, Integer questionNo) {
        if (docJson == null || docJson.isBlank()) return "（无正文切片）";
        try {
            JsonNode root = objectMapper.readTree(docJson);
            JsonNode blocks = root.path("blocks");
            if (!blocks.isArray() || blocks.isEmpty()) return limit(docJson, 30000);

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (JsonNode block : blocks) {
                String text = block.path("text").asText("");
                String type = block.path("type").asText("");
                if ("HEADING".equals(type) || text.contains("问题" + (questionNo == null ? 1 : questionNo)) || count < 25) {
                    sb.append("[").append(block.path("blockId").asText("")).append("] ")
                            .append(text).append("\n");
                    count++;
                }
                if (count >= 30) break;
            }
            return sb.length() > 0 ? sb.toString() : limit(docJson, 20000);
        } catch (Exception e) {
            return limit(docJson, 20000);
        }
    }

    private String extractAssumptionsAndNomenclature(String docJson) {
        if (docJson == null || docJson.isBlank()) return "（无全局符号假设）";
        try {
            JsonNode root = objectMapper.readTree(docJson);
            JsonNode blocks = root.path("blocks");
            if (!blocks.isArray()) return "（无）";
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (JsonNode block : blocks) {
                String text = block.path("text").asText("");
                if (text.contains("假设") || text.contains("符号") || text.contains("说明")) {
                    sb.append("[").append(block.path("blockId").asText("")).append("] ")
                            .append(text).append("\n");
                    count++;
                    if (count >= 10) break;
                }
            }
            return sb.length() > 0 ? sb.toString() : "（无独立假设与符号板块）";
        } catch (Exception e) {
            return "（无）";
        }
    }

    private String extractRelatedFindings(ReviewEvidenceSnapshot reviewEvidence, Integer questionNo) {
        if (reviewEvidence == null || reviewEvidence.findings() == null || reviewEvidence.findings().isEmpty()) {
            return "（本次评审未记录显式扣分缺陷）";
        }
        StringBuilder sb = new StringBuilder();
        for (var f : reviewEvidence.findings()) {
            if ("ISSUE".equals(f.type()) || "WEAKNESS".equals(f.type())) {
                sb.append("- 【").append(f.findingId()).append("】 [").append(f.category()).append("] ")
                        .append(f.statement()).append(" (扣分影响: ").append(f.scoreImpact()).append(")\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "（本次评审所有维度均表现优良，请重点审视升华空间）";
    }

    private String limit(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "\n...（按长度规则截断）";
    }

    private record SubTaskExecutionOutcome(
            GroundedSuggestionV3Output.SubTaskSummary summary,
            List<GroundedSuggestionV3Output.Item> items
    ) {}
}
