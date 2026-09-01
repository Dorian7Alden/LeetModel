package com.leetmodel.suggestion.workflow.v2;

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
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 三段依据链齐全且由服务端做标识引用校验的建议 V2。 */
@Component
public class GroundedSuggestionV2Workflow {
    public static final String VERSION = "GROUNDED_SUGGESTION_V2";
    public static final String RESULT_SCHEMA_VERSION = "GROUNDED_SUGGESTION_V2";
    private static final Map<String, Integer> PRIORITIES = Map.of("P0", 0, "P1", 1, "P2", 2, "P3", 3);
    private static final Set<String> CATEGORIES = Set.of("PROBLEM", "ASSUMPTION", "DATA", "MODEL",
            "SOLUTION", "RESULT", "VALIDATION", "SENSITIVITY", "WRITING", "FIGURE", "CITATION", "APPENDIX");

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public GroundedSuggestionV2Workflow(AiClient aiClient, ObjectMapper objectMapper) throws Exception {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.prompt = new ClassPathResource("prompts/grounded-suggestion-v2.st")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public String currentPrompt() { return prompt; }

    public SuggestionWorkflowResult execute(SuggestionTask task, ProblemContextDTO problem,
                                            PaperParseDTO parse,
                                            ReviewEvidenceSnapshot reviewEvidence,
                                            KnowledgeRetrievalResultDTO knowledge) throws Exception {
        String userPrompt = "题目标题：" + problem.getTitle()
                + "\n\n完整题面：\n" + limit(problem.getContentMarkdown(), 35000)
                + "\n\nPDF 解析产物（物理页码与 blockId 是唯一论文定位事实）：\n"
                + limit(parse.getDocumentJson(), 150000)
                + "\n\n锁定评审依据：\n" + reviewEvidence.snapshotJson()
                + "\n\n本次知识检索运行：\n" + objectMapper.writeValueAsString(knowledge);
        String taskId = "task:" + task.getId();
        AiCallContext context = new AiCallContext("ai-suggestion-service",
                AiFeatureCode.PAPER_SUGGESTION, AiOperationCode.GENERATE_SUGGESTION,
                taskId, VERSION, "PROMPT_GROUNDED_SUGGESTION_0001",
                "MODEL_CFG_SUGGESTION_TEXT_0002", null, AiCallPriority.P1,
                task.getAiIdempotencyKey() == null
                        ? "suggestion:task:" + task.getId() + ":attempt:" + task.getAttemptNo()
                        : task.getAiIdempotencyKey(),
                Instant.now().plusSeconds(360));
        AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                List.of(message(AiRole.SYSTEM, task.getPromptSnapshot()),
                        message(AiRole.USER, userPrompt)), 8192, 0.15,
                AiResponseFormat.JSON_OBJECT, false));
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalArgumentException("AI 网关未返回有依据的论文建议");
        }
        GroundedSuggestionV2Output output = objectMapper.readValue(
                response.content(), GroundedSuggestionV2Output.class);
        validate(output, parse, reviewEvidence, knowledge);
        return new SuggestionWorkflowResult(objectMapper.writeValueAsString(output),
                response.model(), response.callId());
    }

    private void validate(GroundedSuggestionV2Output output, PaperParseDTO parse,
                          ReviewEvidenceSnapshot reviewEvidence,
                          KnowledgeRetrievalResultDTO knowledge) throws Exception {
        requireText(output == null ? null : output.overallStrategy(), "overallStrategy");
        if (output.topPriorities() == null || output.topPriorities().isEmpty()) {
            throw new IllegalArgumentException("topPriorities 不能为空");
        }
        if (output.items() == null || output.items().isEmpty() || output.items().size() > 16) {
            throw new IllegalArgumentException("items 必须包含 1 到 16 项");
        }
        var document = objectMapper.readTree(parse.getDocumentJson());
        Map<String, Integer> paperBlocks = new HashMap<>();
        for (var page : document.path("pages")) {
            paperBlocks.put(page.path("blockId").asText(), page.path("physicalPage").asInt());
        }
        Set<String> findingIds = reviewEvidence.findings().stream()
                .map(ReviewEvidenceSnapshot.Finding::findingId).collect(java.util.stream.Collectors.toSet());
        Map<String, KnowledgeCitationDTO> citations = new HashMap<>();
        knowledge.getCitations().forEach(item -> citations.put(item.getCitationId(), item));
        Set<String> suggestionIds = new HashSet<>();
        Set<String> duplicateKeys = new HashSet<>();
        int previousPriority = -1;
        int expectedId = 1;
        for (var item : output.items()) {
            if (!suggestionIds.add(item.suggestionId()) || !item.suggestionId().equals("S-" + expectedId++)) {
                throw new IllegalArgumentException("suggestionId 必须从 S-1 稳定递增且不能重复");
            }
            Integer priority = PRIORITIES.get(item.priority());
            if (priority == null || priority < previousPriority) {
                throw new IllegalArgumentException("建议优先级非法或未排序");
            }
            previousPriority = priority;
            if (!CATEGORIES.contains(item.category())) throw new IllegalArgumentException("建议类别非法");
            requireText(item.problem(), "problem");
            requireText(item.impact(), "impact");
            requireTexts(item.actions(), "actions");
            requireTexts(item.acceptanceCriteria(), "acceptanceCriteria");
            requireReferences(item.paperEvidenceIds(), paperBlocks.keySet(), "paperEvidenceIds");
            requireReferences(item.reviewFindingIds(), findingIds, "reviewFindingIds");
            requireReferences(item.knowledgeCitationIds(), citations.keySet(), "knowledgeCitationIds");
            if (item.target() == null || item.target().physicalPages() == null
                    || item.target().physicalPages().isEmpty()) {
                throw new IllegalArgumentException("target.physicalPages 不能为空");
            }
            Set<Integer> referencedPages = new HashSet<>();
            item.paperEvidenceIds().forEach(id -> referencedPages.add(paperBlocks.get(id)));
            if (item.target().physicalPages().stream().anyMatch(page -> page == null || page < 1
                    || page > parse.getPageCount() || !referencedPages.contains(page))) {
                throw new IllegalArgumentException("目标页码必须与真实论文 blockId 对应");
            }
            if (("P0".equals(item.priority()) || "P1".equals(item.priority()))
                    && item.knowledgeCitationIds().stream().allMatch(id ->
                    "L5".equals(citations.get(id).getAuthorityLevel())
                            || "AUXILIARY_ONLY".equals(citations.get(id).getApplicability()))) {
                throw new IllegalArgumentException("P0/P1 建议不能只由 L5 辅助资料支撑");
            }
            String duplicateKey = item.problem().strip().toLowerCase() + "\u0000"
                    + String.join("|", item.actions()).strip().toLowerCase();
            if (!duplicateKeys.add(duplicateKey)) throw new IllegalArgumentException("建议项实质重复");
        }
    }

    private void requireReferences(List<String> values, Set<String> valid, String field) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> !valid.contains(value))) {
            throw new IllegalArgumentException(field + " 必须引用本次输入中的真实标识");
        }
    }

    private void requireTexts(List<String> values, String field) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " 必须是非空文本数组");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
    }

    private AiMessage message(AiRole role, String text) {
        return new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, text, null)));
    }

    private String limit(String value, int max) {
        if (value == null) return "（无）";
        return value.length() <= max ? value : value.substring(0, max) + "\n（按版本规则截断）";
    }
}
