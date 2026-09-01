package com.leetmodel.suggestion.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.suggestion.entity.SuggestionTask;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 基于题面、评审结果和 PDF 原文生成结构化改善建议。
 */
@Component
public class SuggestionV1Workflow {

    public static final String VERSION = "IMPROVEMENT_V1";
    private static final Set<String> PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> CATEGORIES = Set.of(
            "ASSUMPTION", "MODEL", "SOLUTION", "VALIDATION", "WRITING", "PRESENTATION");
    private static final Map<String, Integer> PRIORITY_ORDER = Map.of(
            "HIGH", 0, "MEDIUM", 1, "LOW", 2);

    private final StorageService storageService;
    private final PdfTextExtractor textExtractor;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public SuggestionV1Workflow(StorageService storageService, PdfTextExtractor textExtractor,
                                AiClient aiClient, ObjectMapper objectMapper) throws Exception {
        this.storageService = storageService;
        this.textExtractor = textExtractor;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.prompt = new ClassPathResource("prompts/improvement-v1.st")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 获取当前提示词快照。
     *
     * @return 提示词
     */
    public String currentPrompt() {
        return prompt;
    }

    /**
     * 执行首版改善建议工作流。
     *
     * @param task 建议任务
     * @param submission 提交摘要
     * @param problem 题目上下文
     * @param review 已完成评审
     * @return 已校验的结构化结果
     */
    public SuggestionWorkflowResult execute(SuggestionTask task, SubmissionReviewDTO submission,
                                            ProblemContextDTO problem, ReviewSummaryDTO review) throws Exception {
        byte[] pdf;
        try (InputStream input = storageService.download(submission.getObjectName())) {
            pdf = input.readAllBytes();
        }
        PdfTextExtractor.ExtractedPaper paper = textExtractor.extract(pdf);
        String userPrompt = buildUserPrompt(problem, review, paper);
        String taskId = task.getId() == null ? "transient:" + UUID.randomUUID() : "task:" + task.getId();
        String attempt = task.getAttemptNo() == null ? "1" : task.getAttemptNo().toString();
        String idempotencyKey = task.getAiIdempotencyKey() == null
                ? "suggestion:" + taskId + ":attempt:" + attempt : task.getAiIdempotencyKey();
        AiCallContext context = new AiCallContext(
                "ai-suggestion-service", AiFeatureCode.PAPER_SUGGESTION,
                AiOperationCode.GENERATE_SUGGESTION, taskId, task.getWorkflowVersion(),
                "PROMPT_PAPER_SUGGESTION_0001", "MODEL_CFG_SUGGESTION_TEXT_0001", null,
                AiCallPriority.P1, idempotencyKey,
                Instant.now().plusSeconds(270));
        AiChatRequest request = new AiChatRequest(
                AiModality.TEXT,
                context,
                List.of(
                        message(AiRole.SYSTEM, task.getPromptSnapshot()),
                        message(AiRole.USER, userPrompt)
                ),
                4096,
                0.1,
                AiResponseFormat.JSON_OBJECT,
                false
        );
        AiChatResponse response = aiClient.chat(request);
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalArgumentException("AI 网关未返回论文建议内容");
        }
        SuggestionV1Output output = objectMapper.readValue(response.content(), SuggestionV1Output.class);
        validate(output, paper.pageCount());
        return new SuggestionWorkflowResult(
                objectMapper.writeValueAsString(output), response.model(), response.callId());
    }

    private AiMessage message(AiRole role, String text) {
        return new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, text, null)));
    }

    private String buildUserPrompt(ProblemContextDTO problem, ReviewSummaryDTO review,
                                   PdfTextExtractor.ExtractedPaper paper) {
        return "题目标题：" + problem.getTitle()
                + "\n\n题面：\n" + limit(problem.getContentMarkdown(), 30000)
                + "\n\n已有评审（工作流 " + review.getWorkflowVersion() + "）：\n"
                + limit(review.getResultJson(), 30000)
                + "\n\n论文共 " + paper.pageCount() + " 页，输入是否截断：" + paper.truncated()
                + "\n论文原文：\n" + paper.text();
    }

    private String limit(String value, int max) {
        if (value == null) return "（无）";
        return value.length() <= max ? value : value.substring(0, max) + "\n（内容已截断）";
    }

    private void validate(SuggestionV1Output output, int pageCount) {
        requireText(output == null ? null : output.summary(), "summary");
        if (output.items() == null || output.items().isEmpty() || output.items().size() > 20) {
            throw new IllegalArgumentException("items 必须包含 1 到 20 项");
        }
        int previousPriority = -1;
        for (SuggestionV1Output.Item item : output.items()) {
            if (item == null || !PRIORITIES.contains(item.priority())) {
                throw new IllegalArgumentException("priority 不符合契约");
            }
            int currentPriority = PRIORITY_ORDER.get(item.priority());
            if (currentPriority < previousPriority) {
                throw new IllegalArgumentException("items 未按优先级排序");
            }
            previousPriority = currentPriority;
            if (!CATEGORIES.contains(item.category())) {
                throw new IllegalArgumentException("category 不符合契约");
            }
            requireText(item.title(), "title");
            requireText(item.action(), "action");
            requireText(item.evidence(), "evidence");
            if (item.page() != null && (item.page() < 1 || item.page() > pageCount)) {
                throw new IllegalArgumentException("page 超出论文页码范围");
            }
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
