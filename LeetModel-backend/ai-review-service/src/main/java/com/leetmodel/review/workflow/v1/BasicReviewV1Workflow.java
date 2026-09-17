package com.leetmodel.review.workflow.v1;

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
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.service.ReviewTaskLogService;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import dev.langchain4j.model.input.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BasicReviewV1Workflow implements ReviewWorkflow {
    public static final String VERSION_CODE = "BASIC_REVIEW_V1";
    public static final long VERSION_ID = 1L;

    private final StorageService storageService;
    private final PdfPageRenderer pageRenderer;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final ReviewTaskLogService logService;
    private final String prompt;

    public BasicReviewV1Workflow(StorageService storageService, PdfPageRenderer pageRenderer, AiClient aiClient,
                                 ObjectMapper objectMapper, ReviewTaskLogService logService) throws Exception {
        this.storageService = storageService;
        this.pageRenderer = pageRenderer;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.logService = logService;
        String source = new ClassPathResource("prompts/basic-review-v1.st")
                .getContentAsString(StandardCharsets.UTF_8);
        this.prompt = PromptTemplate.from(source).apply(Map.of()).text();
    }

    @Override public String versionCode() { return VERSION_CODE; }
    @Override public Long versionId() { return VERSION_ID; }
    @Override public String currentPrompt() { return prompt; }

    @Override
    public ReviewWorkflowResult execute(ReviewTask task, SubmissionReviewDTO submission) throws Exception {
        byte[] pdf = download(task, submission);
        PdfPageRenderer.RenderedPaper paper = render(task, pdf);
        AiChatResponse response = callModel(task, paper);
        BasicReviewV1Output output = validate(task, response.content());
        return new ReviewWorkflowResult(output.score(), objectMapper.writeValueAsString(output),
                response.model(), response.callId());
    }

    private byte[] download(ReviewTask task, SubmissionReviewDTO submission) throws Exception {
        ReviewTaskLog step = logService.start(task, "FETCH_PDF", "获取 PDF", "submissionId=" + submission.getId());
        try (InputStream input = storageService.download(submission.getObjectName())) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length == 0) throw new IllegalArgumentException("提交 PDF 为空");
            logService.succeed(step, "pdfBytes=" + bytes.length, null);
            return bytes;
        } catch (Exception error) {
            logService.fail(step, error);
            throw error;
        }
    }

    private PdfPageRenderer.RenderedPaper render(ReviewTask task, byte[] pdf) throws Exception {
        ReviewTaskLog step = logService.start(task, "RENDER_PAGES", "渲染论文页面", "pdfBytes=" + pdf.length);
        try {
            PdfPageRenderer.RenderedPaper paper = pageRenderer.render(pdf);
            logService.succeed(step, "pages=" + paper.pageCount() + ",imageBytes=" + paper.totalBytes(), null);
            return paper;
        } catch (Exception error) {
            logService.fail(step, error);
            throw error;
        }
    }

    private AiChatResponse callModel(ReviewTask task, PdfPageRenderer.RenderedPaper paper) {
        ReviewTaskLog step = logService.start(task, "CALL_MODEL", "提交多模态评审", "pages=" + paper.pageCount());
        try {
            List<AiContentPart> parts = new ArrayList<>();
            parts.add(new AiContentPart(AiContentType.TEXT, task.getPromptSnapshot(), null));
            paper.pageDataUrls().forEach(url -> parts.add(new AiContentPart(AiContentType.IMAGE_URL, null, url)));
            boolean experiment = task.getId() == null;
            String taskId = experiment
                    ? "experiment:" + (task.getExperimentRunId() == null
                    ? UUID.randomUUID() : task.getExperimentRunId())
                    : "task:" + task.getId();
            String attempt = task.getAttemptNo() == null ? "1" : task.getAttemptNo().toString();
            AiCallContext context = new AiCallContext(
                    "ai-review-service", AiFeatureCode.PAPER_REVIEW,
                    experiment ? AiOperationCode.EXPERIMENT_REVIEW : AiOperationCode.FORMAL_REVIEW,
                    taskId, task.getWorkflowVersion(), "PROMPT_BASIC_REVIEW_0001",
                    task.getModelExecutionConfigVersion() == null
                            ? "MODEL_CFG_REVIEW_MULTIMODAL_0001"
                            : task.getModelExecutionConfigVersion(), task.getEvaluationTaskId(),
                    experiment ? AiCallPriority.P3 : AiCallPriority.P1,
                    experiment && task.getExperimentIdempotencyKey() != null
                            ? task.getExperimentIdempotencyKey()
                            : stableFormalIdempotencyKey(task, taskId, attempt),
                    Instant.now().plusSeconds(540));
            AiChatRequest request = new AiChatRequest(AiModality.MULTIMODAL, context,
                    List.of(new AiMessage(AiRole.USER, parts)), 4096, 0.1,
                    AiResponseFormat.JSON_OBJECT, false);
            AiChatResponse response = aiClient.chat(request);
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new IllegalArgumentException("AI 网关未返回评审内容");
            }
            logService.succeed(step, "model=" + response.model(), response.callId());
            ReviewTaskLog received = logService.start(task, "MODEL_RESPONSE", "接收模型响应",
                    "callId=" + response.callId());
            logService.succeed(received, "responseChars=" + response.content().length(), response.callId());
            return response;
        } catch (RuntimeException error) {
            logService.fail(step, error);
            throw error;
        }
    }

    private String stableFormalIdempotencyKey(ReviewTask task, String taskId, String attempt) {
        if (task.getAiIdempotencyKey() != null && !task.getAiIdempotencyKey().isBlank()) {
            return task.getAiIdempotencyKey();
        }
        return "review:" + taskId + ":attempt:" + attempt;
    }

    private BasicReviewV1Output validate(ReviewTask task, String json) throws Exception {
        ReviewTaskLog step = logService.start(task, "VALIDATE_JSON", "校验 V1 JSON", "responseChars=" + json.length());
        try {
            BasicReviewV1Output output = objectMapper.readValue(json, BasicReviewV1Output.class);
            requireScore(output.score(), "score");
            requireText(output.summary(), "summary");
            if (output.dimensions() == null) throw new IllegalArgumentException("dimensions 缺失");
            validateDimension(output.dimensions().assumptionRationality(), "assumptionRationality");
            validateDimension(output.dimensions().modelCreativity(), "modelCreativity");
            validateDimension(output.dimensions().resultCorrectness(), "resultCorrectness");
            validateDimension(output.dimensions().expressionClarity(), "expressionClarity");
            validateList(output.strengths(), "strengths");
            validateList(output.weaknesses(), "weaknesses");
            validateList(output.suggestions(), "suggestions");
            logService.succeed(step, "score=" + output.score(), null);
            return output;
        } catch (Exception error) {
            logService.fail(step, error);
            throw new IllegalArgumentException("模型输出不符合 BASIC_REVIEW_V1 JSON 契约", error);
        }
    }

    private void validateDimension(BasicReviewV1Output.Dimension dimension, String name) {
        if (dimension == null) throw new IllegalArgumentException(name + " 缺失");
        requireScore(dimension.score(), name + ".score");
        requireText(dimension.comment(), name + ".comment");
    }

    private void requireScore(BigDecimal score, String name) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(name + " 必须位于 0 到 100");
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
    }

    private void validateList(List<String> values, String name) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " 必须是非空文本数组");
        }
    }
}
