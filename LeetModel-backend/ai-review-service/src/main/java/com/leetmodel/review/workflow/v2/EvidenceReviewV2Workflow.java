package com.leetmodel.review.workflow.v2;

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
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.parse.PaperDocumentV1;
import com.leetmodel.review.parse.PaperParseService;
import com.leetmodel.review.parse.PaperParseV1Parser;
import com.leetmodel.review.service.ReviewTaskLogService;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 完整题面 + PAPER_DOCUMENT_V1 + 六项固定量表的证据化评审。 */
@Component
public class EvidenceReviewV2Workflow implements ReviewWorkflow {
    public static final String VERSION_CODE = "EVIDENCE_REVIEW_V2";
    public static final long VERSION_ID = 2L;
    public static final String RESULT_SCHEMA_VERSION = "EVIDENCE_REVIEW_V2";
    public static final String SCORING_RULE_VERSION = "MODELING_TRAINING_RUBRIC_V2";
    private static final Map<String, BigDecimal> MAX_SCORES = Map.of(
            "PROBLEM_COVERAGE", BigDecimal.valueOf(15),
            "ASSUMPTION_DATA", BigDecimal.valueOf(15),
            "MODEL", BigDecimal.valueOf(25),
            "SOLUTION_RESULT", BigDecimal.valueOf(20),
            "VALIDATION", BigDecimal.valueOf(15),
            "WRITING_REPRODUCIBILITY", BigDecimal.valueOf(10));
    private static final Set<String> COVERAGE_STATUSES = Set.of(
            "COMPLETED", "PARTIAL", "MISSING", "UNVERIFIABLE");
    private static final Set<String> FINDING_TYPES = Set.of("STRENGTH", "ISSUE");
    private static final Set<String> SEVERITIES = Set.of("BLOCKING", "HIGH", "MEDIUM", "LOW");

    private final PaperParseService parseService;
    private final ProblemFeignClient problemFeignClient;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final ReviewTaskLogService logService;
    private final String prompt;

    public EvidenceReviewV2Workflow(PaperParseService parseService,
                                    ProblemFeignClient problemFeignClient,
                                    AiClient aiClient, ObjectMapper objectMapper,
                                    ReviewTaskLogService logService) throws Exception {
        this.parseService = parseService;
        this.problemFeignClient = problemFeignClient;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.logService = logService;
        this.prompt = new ClassPathResource("prompts/evidence-review-v2.st")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override public String versionCode() { return VERSION_CODE; }
    @Override public Long versionId() { return VERSION_ID; }
    @Override public String currentPrompt() { return prompt; }

    @Override
    public ReviewWorkflowResult execute(ReviewTask task, SubmissionReviewDTO submission) throws Exception {
        ReviewTaskLog parseLog = logService.start(task, "ENSURE_PARSE", "确保 PDF 解析产物",
                "workflow=" + PaperParseV1Parser.WORKFLOW_VERSION);
        PaperParseDTO parse;
        try {
            parse = parseService.ensure(submission.getId(), PaperParseV1Parser.WORKFLOW_VERSION);
            logService.succeed(parseLog, "artifactId=" + parse.getArtifactId()
                    + ",status=" + parse.getStatus(), null);
        } catch (RuntimeException exception) {
            logService.fail(parseLog, exception);
            throw exception;
        }
        if (!("SUCCESS".equals(parse.getStatus()) || "PARTIAL_SUCCESS".equals(parse.getStatus()))) {
            throw new IllegalArgumentException("V2 评审要求可用的 PAPER_DOCUMENT_V1 产物");
        }
        PaperDocumentV1 document = objectMapper.readValue(parse.getDocumentJson(), PaperDocumentV1.class);
        if (document.quality().readablePages() < 1) {
            throw new IllegalArgumentException("PDF 解析质量未达到 V2 最低门槛");
        }
        ProblemContextDTO problem = requiredProblem(submission.getProblemId());
        AiChatResponse response = callModel(task, problem, parse);
        EvidenceReviewV2Output output = objectMapper.readValue(
                response.content(), EvidenceReviewV2Output.class);
        validate(output, document);
        return new ReviewWorkflowResult(output.score(), objectMapper.writeValueAsString(output),
                response.model(), response.callId(), parse.getArtifactId());
    }

    private AiChatResponse callModel(ReviewTask task, ProblemContextDTO problem, PaperParseDTO parse) {
        ReviewTaskLog step = logService.start(task, "CALL_EVIDENCE_MODEL", "执行证据化评审",
                "parseArtifactId=" + parse.getArtifactId());
        try {
            String taskKey = task.getId() == null ? "experiment:"
                    + (task.getExperimentRunId() == null ? UUID.randomUUID() : task.getExperimentRunId())
                    : "task:" + task.getId();
            String userPrompt = "题目标题：" + problem.getTitle()
                    + "\n\n完整题面：\n" + limit(problem.getContentMarkdown(), 40000)
                    + "\n\n解析版本：" + parse.getWorkflowVersion()
                    + "，产物：" + parse.getArtifactId()
                    + "，质量：" + parse.getQualityJson()
                    + "\n\nPAPER_DOCUMENT_V1：\n" + limit(parse.getDocumentJson(), 180000);
            AiCallContext context = new AiCallContext("ai-review-service",
                    AiFeatureCode.PAPER_REVIEW,
                    task.getId() == null ? AiOperationCode.EXPERIMENT_REVIEW : AiOperationCode.FORMAL_REVIEW,
                    taskKey, VERSION_CODE, "PROMPT_EVIDENCE_REVIEW_0001",
                    task.getModelExecutionConfigVersion() == null
                            ? "MODEL_CFG_REVIEW_TEXT_0002" : task.getModelExecutionConfigVersion(),
                    task.getEvaluationTaskId(), task.getId() == null ? AiCallPriority.P3 : AiCallPriority.P1,
                    idempotencyKey(task, taskKey), Instant.now().plusSeconds(420));
            AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT, context,
                    List.of(message(AiRole.SYSTEM, task.getPromptSnapshot()),
                            message(AiRole.USER, userPrompt)),
                    8192, 0.1, AiResponseFormat.JSON_OBJECT, false));
            if (response == null || response.content() == null || response.content().isBlank()) {
                throw new IllegalArgumentException("AI 网关未返回 V2 评审内容");
            }
            logService.succeed(step, "model=" + response.model(), response.callId());
            return response;
        } catch (RuntimeException exception) {
            logService.fail(step, exception);
            throw exception;
        }
    }

    private String idempotencyKey(ReviewTask task, String taskKey) {
        if (task.getExperimentIdempotencyKey() != null) return task.getExperimentIdempotencyKey();
        if (task.getAiIdempotencyKey() != null && !task.getAiIdempotencyKey().isBlank()) {
            return task.getAiIdempotencyKey();
        }
        return "evidence-review:" + taskKey + ":attempt:" + task.getAttemptNo();
    }

    private void validate(EvidenceReviewV2Output output, PaperDocumentV1 document) {
        requireText(output == null ? null : output.overallAssessment(), "overallAssessment");
        if (!"PLATFORM_TRAINING_SCORE".equals(output.scoreNature())) {
            throw new IllegalArgumentException("scoreNature 必须声明平台训练评分");
        }
        if (output.scoringRule() == null
                || !SCORING_RULE_VERSION.equals(output.scoringRule().version())) {
            throw new IllegalArgumentException("评分规则版本不匹配");
        }
        if (output.dimensions() == null || output.dimensions().size() != MAX_SCORES.size()) {
            throw new IllegalArgumentException("dimensions 必须恰好包含六项");
        }
        Map<String, EvidenceReviewV2Output.PaperEvidence> evidence = new HashMap<>();
        Set<String> blockIds = new HashSet<>();
        document.pages().forEach(page -> blockIds.add(page.blockId()));
        if (output.evidence() == null || output.evidence().isEmpty()) {
            throw new IllegalArgumentException("evidence 不能为空");
        }
        for (var item : output.evidence()) {
            requireText(item.evidenceId(), "evidenceId");
            if (evidence.put(item.evidenceId(), item) != null) {
                throw new IllegalArgumentException("evidenceId 重复");
            }
            if (item.physicalPage() < 1 || item.physicalPage() > document.pageCount()) {
                throw new IllegalArgumentException("论文证据页码越界");
            }
            if (item.blockId() != null && !item.blockId().isBlank()
                    && !blockIds.contains(item.blockId())) {
                throw new IllegalArgumentException("论文证据 blockId 不存在");
            }
            requireText(item.observation(), "evidence.observation");
        }
        Map<String, EvidenceReviewV2Output.Finding> findings = new HashMap<>();
        if (output.findings() == null || output.findings().isEmpty()) {
            throw new IllegalArgumentException("findings 不能为空");
        }
        for (var finding : output.findings()) {
            requireText(finding.findingId(), "findingId");
            if (!FINDING_TYPES.contains(finding.type()) || !SEVERITIES.contains(finding.severity())) {
                throw new IllegalArgumentException("finding 类型或严重程度非法");
            }
            if (findings.put(finding.findingId(), finding) != null) {
                throw new IllegalArgumentException("findingId 重复");
            }
            requireText(finding.statement(), "finding.statement");
            requireText(finding.scoreImpact(), "finding.scoreImpact");
            requireReferences(finding.evidenceIds(), evidence.keySet(), "finding.evidenceIds");
        }
        BigDecimal sum = BigDecimal.ZERO;
        Set<String> dimensionIds = new HashSet<>();
        for (var dimension : output.dimensions()) {
            BigDecimal max = MAX_SCORES.get(dimension.dimensionId());
            if (max == null || !dimensionIds.add(dimension.dimensionId())
                    || dimension.maxScore() == null || dimension.maxScore().compareTo(max) != 0
                    || dimension.score() == null || dimension.score().compareTo(BigDecimal.ZERO) < 0
                    || dimension.score().compareTo(max) > 0) {
                throw new IllegalArgumentException("评分项或分值不符合固定量表");
            }
            requireText(dimension.reason(), "dimension.reason");
            requireOptionalReferences(dimension.positiveFindingIds(), findings.keySet());
            requireOptionalReferences(dimension.deductionFindingIds(), findings.keySet());
            sum = sum.add(dimension.score());
        }
        if (!dimensionIds.equals(MAX_SCORES.keySet()) || output.score() == null
                || output.score().compareTo(sum) != 0) {
            throw new IllegalArgumentException("总分必须等于六项分数之和");
        }
        if (output.requirementCoverage() == null || output.requirementCoverage().isEmpty()) {
            throw new IllegalArgumentException("requirementCoverage 不能为空");
        }
        Set<String> requirementIds = new HashSet<>();
        for (var requirement : output.requirementCoverage()) {
            if (!requirementIds.add(requirement.requirementId())
                    || !COVERAGE_STATUSES.contains(requirement.status())) {
                throw new IllegalArgumentException("题目要求编号或覆盖状态非法");
            }
            requireText(requirement.requirement(), "requirement");
            requireText(requirement.explanation(), "requirement.explanation");
            if (!"MISSING".equals(requirement.status())) {
                requireReferences(requirement.evidenceIds(), evidence.keySet(), "requirement.evidenceIds");
            }
        }
    }

    private void requireReferences(List<String> values, Set<String> valid, String field) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> !valid.contains(value))) {
            throw new IllegalArgumentException(field + " 必须引用本次结果中的真实标识");
        }
    }

    private void requireOptionalReferences(List<String> values, Set<String> valid) {
        if (values != null && values.stream().anyMatch(value -> !valid.contains(value))) {
            throw new IllegalArgumentException("评分项引用了不存在的 findingId");
        }
    }

    private ProblemContextDTO requiredProblem(Long problemId) {
        Result<ProblemContextDTO> result = problemFeignClient.getProblemContext(problemId);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("problem-service 暂不可用");
        }
        return result.getData();
    }

    private AiMessage message(AiRole role, String text) {
        return new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, text, null)));
    }

    private String limit(String value, int max) {
        if (value == null) return "（无）";
        return value.length() <= max ? value : value.substring(0, max) + "\n（输入已按版本规则截断）";
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
    }
}
