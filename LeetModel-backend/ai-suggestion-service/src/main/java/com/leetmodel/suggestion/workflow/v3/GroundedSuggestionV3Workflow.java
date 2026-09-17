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
import java.util.Locale;
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
    public static final String MODEL_EXECUTION_CONFIG_VERSION = "MODEL_CFG_SUGGESTION_TEXT_0003";
    public static final String MODEL_NAME = "gemini-3.8-flash-high";
    public static final int MAX_OUTPUT_TOKENS = 8192;
    public static final double TEMPERATURE = 0.15;
    private static final int MAX_SUGGESTION_ITEMS = 16;
    private static final Map<String, Integer> PRIORITIES = Map.of("P0", 0, "P1", 1, "P2", 2, "P3", 3);
    private static final Set<String> CATEGORIES = Set.of("PROBLEM", "ASSUMPTION", "DATA", "MODEL",
            "SOLUTION", "RESULT", "VALIDATION", "SENSITIVITY", "WRITING", "FIGURE", "CITATION", "APPENDIX");
    private static final Map<String, String> CATEGORY_ALIASES = Map.ofEntries(
            Map.entry("ALGORITHM", "SOLUTION"),
            Map.entry("OPTIMIZATION", "SOLUTION"),
            Map.entry("MECHANISM", "MODEL"),
            Map.entry("UNCERTAINTY", "SENSITIVITY"),
            Map.entry("ROBUSTNESS", "VALIDATION"),
            Map.entry("VERIFICATION", "VALIDATION"),
            Map.entry("EVALUATION", "VALIDATION"),
            Map.entry("VISUALIZATION", "FIGURE"),
            Map.entry("PRESENTATION", "WRITING"),
            Map.entry("METHOD", "MODEL"),
            Map.entry("METHODOLOGY", "MODEL"),
            Map.entry("ANALYSIS", "PROBLEM"),
            Map.entry("EXPERIMENT", "VALIDATION")
    );

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeRetrievalFeignClient knowledgeFeignClient;
    private final Executor subTaskExecutor;

    private final String plannerPromptTemplate;
    private final String subTaskPromptTemplate;
    private final String synthesizerPromptTemplate;
    private final String plannerPromptTemplateV4;
    private final String subTaskPromptTemplateV4;
    private final String synthesizerPromptTemplateV4;

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
        this.plannerPromptTemplateV4 = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-suggestion-planner-v4.st");
        this.subTaskPromptTemplateV4 = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase2-subtask-suggestion-v4.st");
        this.synthesizerPromptTemplateV4 = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase3-suggestion-synthesizer-v4.st");
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
        finalOutput = normalizeAndSort(finalOutput, summaries, candidateItems);

        // 5. 丢弃缺少完整依据链的单个候选项，保留其余可验证建议
        finalOutput = retainVerifiableItems(finalOutput, parse, reviewEvidence, isV4(task));

        // 6. 规整 topPriorities 保证严格符合 1 到 3 项要求
        List<String> finalTopPriorities = finalOutput.topPriorities();
        if (finalTopPriorities == null || finalTopPriorities.isEmpty()) {
            finalTopPriorities = finalOutput.items().stream()
                    .map(it -> it.title() != null ? it.title() : "推进论文修改")
                    .limit(3)
                    .toList();
        } else if (finalTopPriorities.size() > 3) {
            finalTopPriorities = finalTopPriorities.subList(0, 3);
        }
        finalOutput = new GroundedSuggestionV3Output(
                finalOutput.workflowVersion(),
                finalOutput.overallStrategy(),
                finalTopPriorities,
                finalOutput.subTaskSummaries(),
                finalOutput.items()
        );

        // 7. 服务端确定性强校验
        validate(finalOutput, parse, reviewEvidence, !isV4(task));

        String resultJson = objectMapper.writeValueAsString(finalOutput);
        return new SuggestionWorkflowResult(resultJson, MODEL_NAME, "call-sug-v3-" + task.getId());
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
            boolean v4 = isV4(task);
            String userPrompt = PromptTemplateRenderer.render(
                    v4 ? plannerPromptTemplateV4 : plannerPromptTemplate,
                    variables
            );
            String callId = callId(task, "planner");
            AiCallContext context = new AiCallContext("ai-suggestion-service",
                    AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                    callId, workflowVersion(task), v4 ? "PROMPT_PLANNER_0002" : "PROMPT_PLANNER_0001",
                    modelConfigVersion(task), null, AiCallPriority.P1,
                    callId, Instant.now().plusSeconds(180));
            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))),
                    MAX_OUTPUT_TOKENS, TEMPERATURE, AiResponseFormat.JSON_OBJECT, false));

            if (response != null && response.content() != null && !response.content().isBlank()) {
                String content = response.content().strip();
                if (content.startsWith("[")) {
                    List<SuggestionPlannerOutput.PlannerTask> tasks = objectMapper.readValue(
                            content, new com.fasterxml.jackson.core.type.TypeReference<List<SuggestionPlannerOutput.PlannerTask>>() {}
                    );
                    return new SuggestionPlannerOutput(tasks);
                }
                return V3OutputParser.parse(objectMapper, content, SuggestionPlannerOutput.class);
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

            boolean v4 = isV4(task);
            String userPrompt = PromptTemplateRenderer.render(
                    v4 ? subTaskPromptTemplateV4 : subTaskPromptTemplate,
                    variables
            );
            String callId = subTaskCallId(task, taskId);
            AiCallContext context = new AiCallContext("ai-suggestion-service",
                    AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                    callId, workflowVersion(task), v4 ? "PROMPT_SUBTASK_0002" : "PROMPT_SUBTASK_0001",
                    modelConfigVersion(task), null, AiCallPriority.P1,
                    callId, Instant.now().plusSeconds(180));

            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))),
                    MAX_OUTPUT_TOKENS, TEMPERATURE, AiResponseFormat.JSON_OBJECT, false));

            if (response != null && response.content() != null && !response.content().isBlank()) {
                String content = response.content().strip();
                List<GroundedSuggestionV3Output.Item> items;
                if (content.startsWith("[")) {
                    items = objectMapper.readValue(content, new com.fasterxml.jackson.core.type.TypeReference<List<GroundedSuggestionV3Output.Item>>() {});
                } else {
                    SubTaskSuggestionOutput output = V3OutputParser.parse(objectMapper, content, SubTaskSuggestionOutput.class);
                    items = output.suggestions() != null ? output.suggestions() : List.of();
                }
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
            boolean v4 = isV4(task);
            String userPrompt = PromptTemplateRenderer.render(
                    v4 ? synthesizerPromptTemplateV4 : synthesizerPromptTemplate,
                    variables
            );
            String callId = callId(task, "synth");
            AiCallContext context = new AiCallContext("ai-suggestion-service",
                    AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                    callId, workflowVersion(task), v4 ? "PROMPT_SYNTHESIZER_0002" : "PROMPT_SYNTHESIZER_0001",
                    modelConfigVersion(task), null, AiCallPriority.P1,
                    callId, Instant.now().plusSeconds(180));
            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))),
                    MAX_OUTPUT_TOKENS, TEMPERATURE, AiResponseFormat.JSON_OBJECT, false));

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
            List<GroundedSuggestionV3Output.SubTaskSummary> summaries,
            List<GroundedSuggestionV3Output.Item> candidateItems
    ) {
        List<GroundedSuggestionV3Output.Item> synthesizedItems = output.items() != null
                ? output.items()
                : List.of();
        List<GroundedSuggestionV3Output.Item> rawItems = synthesizedItems.isEmpty()
                ? candidateItems
                : synthesizedItems;
        List<GroundedSuggestionV3Output.Item> sorted = new ArrayList<>(rawItems);
        sorted.sort(Comparator.comparingInt(item -> PRIORITIES.getOrDefault(item.priority(), 2)));

        List<GroundedSuggestionV3Output.Item> normalized = new ArrayList<>();
        Set<String> normalizedKeys = new HashSet<>();
        int id = 1;
        for (var item : sorted) {
            String normalizedKey = normalizedItemKey(item);
            if (!normalizedKeys.add(normalizedKey)) {
                continue;
            }
            normalized.add(new GroundedSuggestionV3Output.Item(
                    "S-" + (id++),
                    item.priority() == null ? "P2" : item.priority(),
                    item.type() == null ? "CORRECTION" : item.type(),
                    canonicalCategory(item.category()),
                    item.subProblemNo(),
                    item.title() == null ? "修改建议" : item.title(),
                    item.problemOrGap() == null ? "" : item.problemOrGap(),
                    item.diagnosis(),
                    item.targetLocation(),
                    item.actionPlanMarkdown() == null ? "请根据指导建议进行修改" : item.actionPlanMarkdown(),
                    item.acceptanceCriteria() == null || item.acceptanceCriteria().isEmpty() ? List.of("完成模型修正") : item.acceptanceCriteria(),
                    item.evidenceChain()
            ));
            if (normalized.size() == MAX_SUGGESTION_ITEMS) {
                break;
            }
        }
        List<String> topPriorities = output.topPriorities();
        if (topPriorities == null || topPriorities.isEmpty()) {
            topPriorities = normalized.stream()
                    .map(it -> it.title() != null ? it.title() : "推进论文修改")
                    .limit(3)
                    .toList();
        } else if (topPriorities.size() > 3) {
            topPriorities = topPriorities.subList(0, 3);
        }
        return new GroundedSuggestionV3Output(VERSION, output.overallStrategy(), topPriorities, summaries, normalized);
    }

    private String normalizedItemKey(GroundedSuggestionV3Output.Item item) {
        String title = item.title() == null ? "" : item.title().strip().toLowerCase();
        String problemOrGap = item.problemOrGap() == null ? "" : item.problemOrGap().strip().toLowerCase();
        return title + "\0" + problemOrGap;
    }

    private GroundedSuggestionV3Output retainVerifiableItems(
            GroundedSuggestionV3Output output,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence,
            boolean v4
    ) throws Exception {
        Set<String> paperBlockIds = extractPaperBlockIds(parse);
        Set<String> findingIds = reviewEvidence != null && reviewEvidence.findings() != null
                ? reviewEvidence.findings().stream()
                .map(ReviewEvidenceSnapshot.Finding::findingId)
                .collect(java.util.stream.Collectors.toSet())
                : Collections.emptySet();

        List<GroundedSuggestionV3Output.Item> retained = new ArrayList<>();
        for (var item : output.items()) {
            if (!hasCompleteEvidenceChain(
                    item,
                    paperBlockIds,
                    findingIds,
                    !v4,
                    v4
            )) {
                continue;
            }
            retained.add(withSuggestionId(item, "S-" + (retained.size() + 1)));
        }
        int discardedCount = output.items().size() - retained.size();
        if (discardedCount > 0) {
            log.info("建议候选依据链校验完成: retained={}, discarded={}", retained.size(), discardedCount);
        }
        List<String> topPriorities = output.topPriorities();
        if (topPriorities == null || topPriorities.isEmpty()) {
            topPriorities = retained.stream()
                    .map(it -> it.title() != null ? it.title() : "推进论文修改")
                    .limit(3)
                    .toList();
        } else if (topPriorities.size() > 3) {
            topPriorities = topPriorities.subList(0, 3);
        }
        return new GroundedSuggestionV3Output(
                output.workflowVersion(),
                output.overallStrategy(),
                topPriorities,
                output.subTaskSummaries(),
                retained
        );
    }

    private boolean hasCompleteEvidenceChain(
            GroundedSuggestionV3Output.Item item,
            Set<String> paperBlockIds,
            Set<String> findingIds,
            boolean requireKnowledgeCitation,
            boolean requireSingleReviewFinding
    ) {
        var evidence = item.evidenceChain();
        if (evidence == null) {
            log.info("Discarded item: evidence is null, title={}", item.title());
            return false;
        }
        if (evidence.paperEvidenceIds() == null || evidence.paperEvidenceIds().isEmpty()) {
            log.info("Discarded item: paperEvidenceIds empty, title={}", item.title());
            return false;
        }
        if (requireKnowledgeCitation
                && (evidence.knowledgeCitationIds() == null
                || evidence.knowledgeCitationIds().isEmpty())) {
            log.info("Discarded item: knowledgeCitationIds empty, title={}", item.title());
            return false;
        }
        if (paperBlockIds.isEmpty() || !paperBlockIds.containsAll(evidence.paperEvidenceIds())) {
            log.info("Discarded item: paperBlockIds mismatch, required={}, title={}", evidence.paperEvidenceIds(), item.title());
            return false;
        }
        List<String> reviewFindingIds = evidence.reviewFindingIds() == null ? List.of() : evidence.reviewFindingIds();
        if ("CORRECTION".equals(item.type()) && reviewFindingIds.isEmpty()) {
            log.info("Discarded item: CORRECTION reviewFindingIds empty, title={}", item.title());
            return false;
        }
        if (requireSingleReviewFinding && reviewFindingIds.size() > 1) {
            log.info(
                    "Discarded item: multiple reviewFindingIds are not allowed in V4, required={}, title={}",
                    reviewFindingIds,
                    item.title()
            );
            return false;
        }
        if (!findingIds.containsAll(reviewFindingIds)) {
            log.info("Discarded item: reviewFindingIds mismatch, required={}, actualFindings={}, title={}", reviewFindingIds, findingIds, item.title());
            return false;
        }
        return true;
    }

    private GroundedSuggestionV3Output.Item withSuggestionId(
            GroundedSuggestionV3Output.Item item,
            String suggestionId
    ) {
        return new GroundedSuggestionV3Output.Item(
                suggestionId,
                item.priority(),
                item.type(),
                canonicalCategory(item.category()),
                item.subProblemNo(),
                item.title(),
                item.problemOrGap(),
                item.diagnosis(),
                item.targetLocation(),
                item.actionPlanMarkdown(),
                item.acceptanceCriteria(),
                item.evidenceChain()
        );
    }

    private Set<String> extractPaperBlockIds(PaperParseDTO parse) throws Exception {
        if (parse == null || parse.getDocumentJson() == null || parse.getDocumentJson().isBlank()) {
            return Collections.emptySet();
        }
        JsonNode doc = objectMapper.readTree(parse.getDocumentJson());
        Set<String> blockIds = new HashSet<>();
        for (JsonNode block : doc.path("blocks")) {
            String blockId = block.path("blockId").asText();
            if (!blockId.isBlank()) {
                blockIds.add(blockId);
            }
        }
        if (blockIds.isEmpty()) {
            for (JsonNode page : doc.path("pages")) {
                String blockId = page.path("blockId").asText();
                if (!blockId.isBlank()) {
                    blockIds.add(blockId);
                }
            }
        }
        return blockIds;
    }

    /**
     * 将模型常见同义类别收敛到已发布结果 Schema，避免扩大对外枚举。
     */
    private String canonicalCategory(String category) {
        if (category == null || category.isBlank()) return "MODEL";
        String normalized = category.strip().toUpperCase();
        String mapped = CATEGORY_ALIASES.getOrDefault(normalized, normalized);
        return CATEGORIES.contains(mapped) ? mapped : "MODEL";
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

            Map<String, Integer> blockIndex = new HashMap<>();
            for (int index = 0; index < blocks.size(); index++) {
                String blockId = blocks.get(index).path("blockId").asText();
                if (!blockId.isBlank()) blockIndex.put(blockId, index);
            }

            Set<Integer> selectedIndexes = new java.util.TreeSet<>();
            JsonNode sections = root.path("sections");
            if (sectionIds != null && !sectionIds.isEmpty() && sections.isArray()) {
                List<JsonNode> sectionList = new ArrayList<>();
                sections.forEach(sectionList::add);
                for (int sectionIndex = 0; sectionIndex < sectionList.size(); sectionIndex++) {
                    JsonNode section = sectionList.get(sectionIndex);
                    if (!sectionIds.contains(section.path("sectionId").asText())) continue;
                    Integer start = blockIndex.get(section.path("headingBlockId").asText());
                    if (start == null) continue;
                    int end = blocks.size();
                    for (int next = sectionIndex + 1; next < sectionList.size(); next++) {
                        Integer nextStart = blockIndex.get(
                                sectionList.get(next).path("headingBlockId").asText()
                        );
                        if (nextStart != null && nextStart > start) {
                            end = nextStart;
                            break;
                        }
                    }
                    for (int index = start; index < end; index++) {
                        selectedIndexes.add(index);
                    }
                }
            }

            if (selectedIndexes.isEmpty()) {
                String questionMarker = questionNo == null || questionNo <= 0
                        ? ""
                        : String.valueOf(questionNo);
                for (int index = 0; index < blocks.size(); index++) {
                    JsonNode block = blocks.get(index);
                    String text = block.path("text").asText("");
                    if ("HEADING".equals(block.path("type").asText())
                            && (questionMarker.isBlank()
                            || text.contains("问题" + questionMarker)
                            || text.toLowerCase(Locale.ROOT).contains("problem " + questionMarker)
                            || text.toLowerCase(Locale.ROOT).contains("model " + questionMarker))) {
                        int end = Math.min(blocks.size(), index + 45);
                        for (int current = index; current < end; current++) {
                            selectedIndexes.add(current);
                        }
                    }
                }
            }

            if (selectedIndexes.isEmpty()) {
                for (int index = 0; index < Math.min(35, blocks.size()); index++) {
                    selectedIndexes.add(index);
                }
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Integer index : selectedIndexes) {
                sb.append(formatBlockForPrompt(blocks.get(index))).append("\n\n");
                if (++count >= 80) break;
            }
            return sb.length() > 0 ? sb.toString() : limit(docJson, 20000);
        } catch (Exception e) {
            return limit(docJson, 20000);
        }
    }

    private String formatBlockForPrompt(JsonNode block) {
        String blockId = block.path("blockId").asText("");
        int page = block.path("physicalPage").asInt(1);
        String type = block.path("type").asText("PARAGRAPH");
        String content = block.path("text").asText("");
        if (content.isBlank() && block.path("formula").isObject()) {
            content = "$$" + block.path("formula").path("latex").asText("") + "$$";
        }
        if (content.isBlank() && block.path("table").isObject()) {
            content = block.path("table").path("html").asText("");
        }
        if (content.isBlank() && block.path("figure").isObject()) {
            content = block.path("figure").path("caption").asText("") + " "
                    + block.path("figure").path("description").asText("");
        }
        if (content.isBlank() && block.path("code").isObject()) {
            content = block.path("code").path("codeContent").asText("");
        }
        return "[" + blockId + " | 第 " + page + " 页 | " + type + "]\n" + content;
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
                sb.append("- 【").append(f.findingId()).append("】 [").append(f.category()).append("]\n")
                        .append("  - 评审结论：").append(f.statement()).append("\n");
                if (f.rationaleMarkdown() != null && !f.rationaleMarkdown().isBlank()) {
                    sb.append("  - 为什么重要：").append(f.rationaleMarkdown()).append("\n");
                }
                List<String> evidenceIds = f.paperEvidenceIds() == null
                        ? List.of()
                        : f.paperEvidenceIds();
                sb.append("  - 论文证据块（引用该问题时必须完整保留）：")
                        .append(evidenceIds.isEmpty() ? "无" : String.join(", ", evidenceIds))
                        .append("\n");
                String scoreImpact = meaningfulScoreImpact(f.scoreImpact());
                if (scoreImpact != null) {
                    sb.append("  - 评分影响：").append(scoreImpact).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : "（本次评审所有维度均表现优良，请重点审视升华空间）";
    }

    private String meaningfulScoreImpact(String value) {
        if (value == null || value.isBlank()) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("-?\\d+(?:\\.\\d+)?")
                .matcher(value);
        boolean foundNumber = false;
        while (matcher.find()) {
            foundNumber = true;
            if (Math.abs(Double.parseDouble(matcher.group())) > 0.000001D) {
                return value;
            }
        }
        return foundNumber ? null : value;
    }

    private String limit(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        int boundary = Math.max(
                text.lastIndexOf('\n', max),
                Math.max(text.lastIndexOf('。', max), text.lastIndexOf('.', max))
        );
        int end = boundary >= max / 2 ? boundary + 1 : max;
        return text.substring(0, end);
    }

    private boolean isV4(SuggestionTask task) {
        return "GROUNDED_SUGGESTION_V4".equals(task.getWorkflowVersion());
    }

    private String workflowVersion(SuggestionTask task) {
        return isV4(task) ? "GROUNDED_SUGGESTION_V4" : VERSION;
    }

    private String modelConfigVersion(SuggestionTask task) {
        return isV4(task) ? "MODEL_CFG_SUGGESTION_TEXT_0004" : MODEL_EXECUTION_CONFIG_VERSION;
    }

    /**
     * 构造包含物理尝试序号的 AI 调用标识。
     *
     * @param task 当前建议任务
     * @param stage AI 调用阶段
     * @return 可同时作为业务任务标识和幂等键的稳定字符串
     */
    private String callId(SuggestionTask task, String stage) {
        int attempt = task.getAttemptNo() == null || task.getAttemptNo() <= 0
                ? 1
                : task.getAttemptNo();
        return "sug:" + stage + ":" + task.getId() + ":attempt:" + attempt;
    }

    /**
     * 构造子任务 AI 调用标识，并限制模型生成标识占用的长度。
     *
     * @param task 当前建议任务
     * @param subTaskId 模型规划的子任务标识
     * @return 不超过 AiCallContext 长度限制的稳定调用标识
     */
    private String subTaskCallId(SuggestionTask task, String subTaskId) {
        return callId(task, "subtask") + ":" + stableCallSegment(subTaskId);
    }

    /**
     * 将外部生成的标识转换为可安全进入调用上下文的稳定片段。
     *
     * @param value 原始标识
     * @return 最长 48 字符的稳定片段
     */
    private String stableCallSegment(String value) {
        String normalized = value == null
                ? "unknown"
                : value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.length() <= 48) return normalized;
        String suffix = Integer.toUnsignedString(normalized.hashCode(), 16);
        return normalized.substring(0, 39) + "-" + suffix;
    }

    private record SubTaskExecutionOutcome(
            GroundedSuggestionV3Output.SubTaskSummary summary,
            List<GroundedSuggestionV3Output.Item> items
    ) {}

    private void validate(
            GroundedSuggestionV3Output output,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence,
            boolean requireKnowledgeCitation
    ) throws Exception {
        requireText(output == null ? null : output.overallStrategy(), "overallStrategy");
        if (output.topPriorities() == null || output.topPriorities().isEmpty()) {
            throw new IllegalArgumentException("topPriorities 必须包含 1 到 3 项");
        }
        if (output.items() == null || output.items().isEmpty()) {
            throw new IllegalArgumentException("缺少可验证建议");
        }
        if (output.items().size() > MAX_SUGGESTION_ITEMS) {
            throw new IllegalArgumentException("items 必须包含 1 到 16 项");
        }

        Map<String, Integer> paperBlocks = new HashMap<>();
        if (parse != null && parse.getDocumentJson() != null && !parse.getDocumentJson().isBlank()) {
            JsonNode doc = objectMapper.readTree(parse.getDocumentJson());
            for (JsonNode block : doc.path("blocks")) {
                String id = block.path("blockId").asText();
                if (!id.isBlank()) {
                    paperBlocks.put(id, block.path("physicalPage").asInt(1));
                }
            }
            if (paperBlocks.isEmpty()) {
                for (JsonNode page : doc.path("pages")) {
                    String id = page.path("blockId").asText();
                    if (!id.isBlank()) {
                        paperBlocks.put(id, page.path("physicalPage").asInt(1));
                    }
                }
            }
        }

        Set<String> findingIds = reviewEvidence != null && reviewEvidence.findings() != null
                ? reviewEvidence.findings().stream().map(ReviewEvidenceSnapshot.Finding::findingId).collect(java.util.stream.Collectors.toSet())
                : Collections.emptySet();

        Set<String> suggestionIds = new HashSet<>();
        Set<String> duplicateKeys = new HashSet<>();
        int previousPriority = -1;
        int expectedId = 1;

        for (var item : output.items()) {
            if (!suggestionIds.add(item.suggestionId()) || !item.suggestionId().equals("S-" + expectedId++)) {
                throw new IllegalArgumentException("suggestionId 必须从 S-1 稳定递增且不能重复: " + item.suggestionId());
            }
            Integer priority = PRIORITIES.get(item.priority());
            if (priority == null || priority < previousPriority) {
                throw new IllegalArgumentException("建议优先级非法或未按降序排列: " + item.priority());
            }
            previousPriority = priority;
            if (!CATEGORIES.contains(item.category())) {
                throw new IllegalArgumentException("建议类别非法: " + item.category());
            }
            requireText(item.title(), "title");
            requireText(item.problemOrGap(), "problemOrGap");
            requireText(item.actionPlanMarkdown(), "actionPlanMarkdown");
            requireTexts(item.acceptanceCriteria(), "acceptanceCriteria");

            var evidence = item.evidenceChain();
            if (evidence == null) {
                throw new IllegalArgumentException("建议项缺少依据链契约 (evidenceChain)");
            }
            if (evidence.paperEvidenceIds() == null || evidence.paperEvidenceIds().isEmpty()) {
                throw new IllegalArgumentException("paperEvidenceIds 不能为空");
            }
            if (!paperBlocks.isEmpty()) {
                for (String blockId : evidence.paperEvidenceIds()) {
                    if (!paperBlocks.containsKey(blockId)) {
                        throw new IllegalArgumentException("paperEvidenceIds 必须引用真实存在的论文 blockId: " + blockId);
                    }
                }
            }

            if ("CORRECTION".equals(item.type())) {
                if (evidence.reviewFindingIds() == null || evidence.reviewFindingIds().isEmpty()) {
                    throw new IllegalArgumentException("CORRECTION 修复类建议必须引用评审发现 reviewFindingIds");
                }
            }
            if (requireKnowledgeCitation
                    && (evidence.knowledgeCitationIds() == null
                    || evidence.knowledgeCitationIds().isEmpty())) {
                throw new IllegalArgumentException("knowledgeCitationIds 不能为空");
            }

            String duplicateKey = item.title().strip().toLowerCase() + "\0" + item.problemOrGap().strip().toLowerCase();
            if (!duplicateKeys.add(duplicateKey)) {
                throw new IllegalArgumentException("存在实质重复的建议项: " + item.title());
            }
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    private void requireTexts(List<String> values, String field) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException(field + " 必须是非空文本数组");
        }
    }
}
