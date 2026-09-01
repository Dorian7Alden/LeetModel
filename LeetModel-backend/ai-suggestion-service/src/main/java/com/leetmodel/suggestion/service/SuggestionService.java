package com.leetmodel.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClientException;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.suggestion.config.SuggestionWorkerProperties;
import com.leetmodel.suggestion.dto.SuggestionCreateRequest;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.enums.SuggestionErrorCode;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.messaging.SuggestionReadyMessageService;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceProjector;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.vo.SuggestionVO;
import com.leetmodel.suggestion.vo.SuggestionKnowledgeCitationVO;
import com.leetmodel.suggestion.workflow.SuggestionV1Output;
import com.leetmodel.suggestion.workflow.SuggestionV1Workflow;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
import com.leetmodel.suggestion.workflow.v2.GroundedSuggestionV2Output;
import com.leetmodel.suggestion.workflow.v2.GroundedSuggestionV2Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** 论文建议任务：显式评审选择、动作级幂等、多次生成和三段依据链。 */
@Slf4j
@Service
public class SuggestionService {
    private static final String PAPER_PARSE_VERSION = "PAPER_PARSE_V1";
    private static final String DEFAULT_RETRIEVAL_VERSION = "VECTOR_RAG_V1";

    private final SuggestionTaskMapper taskMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final ProblemFeignClient problemFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final KnowledgeRetrievalFeignClient knowledgeRetrievalFeignClient;
    private final SuggestionV1Workflow v1Workflow;
    private final GroundedSuggestionV2Workflow v2Workflow;
    private final ReviewEvidenceProjector evidenceProjector;
    private final SuggestionReadyMessageService readyMessageService;
    private final SuggestionWorkerProperties workerProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public SuggestionService(SuggestionTaskMapper taskMapper,
                             SubmissionFeignClient submissionFeignClient,
                             ReviewFeignClient reviewFeignClient,
                             ProblemFeignClient problemFeignClient,
                             TeamFeignClient teamFeignClient,
                             KnowledgeRetrievalFeignClient knowledgeRetrievalFeignClient,
                             SuggestionV1Workflow v1Workflow,
                             GroundedSuggestionV2Workflow v2Workflow,
                             ReviewEvidenceProjector evidenceProjector,
                             SuggestionReadyMessageService readyMessageService,
                             SuggestionWorkerProperties workerProperties,
                             ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.submissionFeignClient = submissionFeignClient;
        this.reviewFeignClient = reviewFeignClient;
        this.problemFeignClient = problemFeignClient;
        this.teamFeignClient = teamFeignClient;
        this.knowledgeRetrievalFeignClient = knowledgeRetrievalFeignClient;
        this.v1Workflow = v1Workflow;
        this.v2Workflow = v2Workflow;
        this.evidenceProjector = evidenceProjector;
        this.readyMessageService = readyMessageService;
        this.workerProperties = workerProperties;
        this.objectMapper = objectMapper;
    }

    /** 保留面向服务单元测试的构造契约。 */
    public SuggestionService(SuggestionTaskMapper taskMapper,
                             SubmissionFeignClient submissionFeignClient,
                             ReviewFeignClient reviewFeignClient,
                             ProblemFeignClient problemFeignClient,
                             TeamFeignClient teamFeignClient,
                             KnowledgeRetrievalFeignClient knowledgeRetrievalFeignClient,
                             SuggestionV1Workflow v1Workflow,
                             GroundedSuggestionV2Workflow v2Workflow,
                             ReviewEvidenceProjector evidenceProjector,
                             ObjectMapper objectMapper) {
        this(taskMapper, submissionFeignClient, reviewFeignClient, problemFeignClient, teamFeignClient,
                knowledgeRetrievalFeignClient, v1Workflow, v2Workflow, evidenceProjector,
                null, defaultWorkerProperties(), objectMapper);
    }

    /** 保留给 V1 单元测试和历史嵌入调用的构造契约。 */
    public SuggestionService(SuggestionTaskMapper taskMapper,
                             SubmissionFeignClient submissionFeignClient,
                             ReviewFeignClient reviewFeignClient,
                             ProblemFeignClient problemFeignClient,
                             TeamFeignClient teamFeignClient,
                             SuggestionV1Workflow v1Workflow,
                             ObjectMapper objectMapper) {
        this(taskMapper, submissionFeignClient, reviewFeignClient, problemFeignClient, teamFeignClient,
                null, v1Workflow, null, null, null, defaultWorkerProperties(), objectMapper);
    }

    /** 新建一次独立生成意图；clientRequestId 只对本次用户动作幂等。 */
    @Transactional
    public SuggestionVO create(SuggestionCreateRequest request, Long userId) {
        SubmissionReviewDTO submission = requiredSubmission(request.getSubmissionId());
        checkMember(submission.getTeamId(), userId);
        ReviewSummaryDTO review = requiredCompletedReview(request.getReviewTaskId());
        validateSource(submission, review);
        validateClientRequestId(request.getClientRequestId());
        if (request.getRetrievalWorkflowVersion() != null
                && !DEFAULT_RETRIEVAL_VERSION.equals(request.getRetrievalWorkflowVersion())) {
            throw new IllegalArgumentException("正式论文建议当前只允许 VECTOR_RAG_V1");
        }

        SuggestionTask existing = findByClientRequest(userId, request.getClientRequestId());
        if (existing != null) return toVO(validateIdempotentPayload(existing, submission, review));
        assertAdmissionAvailable();

        SuggestionTask task = new SuggestionTask();
        task.setSubmissionId(submission.getId());
        task.setTeamId(submission.getTeamId());
        task.setProblemId(submission.getProblemId());
        task.setClientRequestId(request.getClientRequestId());
        task.setRequestedByUserId(userId);
        task.setReviewTaskId(review.getTaskId());
        task.setEligibilityReviewTaskId(review.getTaskId());
        task.setEvidenceReviewTaskId(review.getTaskId());
        task.setWorkflowVersion(GroundedSuggestionV2Workflow.VERSION);
        task.setReviewWorkflowVersion(review.getWorkflowVersion());
        task.setPaperParsingWorkflowVersion(PAPER_PARSE_VERSION);
        task.setRetrievalWorkflowVersion(request.getRetrievalWorkflowVersion() == null
                || request.getRetrievalWorkflowVersion().isBlank()
                ? DEFAULT_RETRIEVAL_VERSION : request.getRetrievalWorkflowVersion());
        task.setResultSchemaVersion(GroundedSuggestionV2Workflow.RESULT_SCHEMA_VERSION);
        task.setStatus("WAITING");
        task.setCurrentStage("PREPARING");
        task.setPromptSnapshot(v2Workflow.currentPrompt());
        task.setRetryCount(0);
        task.setAttemptNo(1);
        task.setMaxAttempts(workerProperties.getMaxAttempts());
        task.setTraceId(currentTraceId());
        task.setRecoveryCount(0);
        task.setNextRunAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
            task.setAiIdempotencyKey(aiIdempotencyKey(task.getId(), task.getAttemptNo()));
            enqueueReady(task, 0L);
            return toVO(task);
        } catch (DuplicateKeyException exception) {
            SuggestionTask concurrent = findByClientRequest(userId, request.getClientRequestId());
            if (concurrent != null) {
                return toVO(validateIdempotentPayload(concurrent, submission, review));
            }
            throw exception;
        }
    }

    /** 兼容历史服务内调用，保持 IMPROVEMENT_V1 的单任务语义；公共 API 不再调用此方法。 */
    @Transactional
    public SuggestionVO create(Long submissionId, Long userId) {
        SubmissionReviewDTO submission = requiredSubmission(submissionId);
        checkMember(submission.getTeamId(), userId);
        ReviewSummaryDTO review = requiredCompletedReviewBySubmission(submissionId);
        validateSource(submission, review);
        SuggestionTask existing = taskMapper.selectOne(new LambdaQueryWrapper<SuggestionTask>()
                .eq(SuggestionTask::getSubmissionId, submissionId)
                .eq(SuggestionTask::getWorkflowVersion, SuggestionV1Workflow.VERSION)
                .last("LIMIT 1"));
        if (existing != null) return toVO(existing);
        SuggestionTask task = new SuggestionTask();
        task.setSubmissionId(submissionId); task.setTeamId(submission.getTeamId());
        task.setProblemId(submission.getProblemId()); task.setReviewTaskId(review.getTaskId());
        task.setEligibilityReviewTaskId(review.getTaskId()); task.setEvidenceReviewTaskId(review.getTaskId());
        task.setWorkflowVersion(SuggestionV1Workflow.VERSION);
        task.setReviewWorkflowVersion(review.getWorkflowVersion()); task.setStatus("WAITING");
        task.setCurrentStage("PREPARING"); task.setPromptSnapshot(v1Workflow.currentPrompt());
        task.setRetryCount(0); task.setAttemptNo(1); task.setMaxAttempts(workerProperties.getMaxAttempts());
        task.setTraceId(currentTraceId()); task.setRecoveryCount(0); task.setNextRunAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
            task.setAiIdempotencyKey(aiIdempotencyKey(task.getId(), task.getAttemptNo()));
            enqueueReady(task, 0L);
            return toVO(task);
        } catch (DuplicateKeyException exception) {
            SuggestionTask concurrent = taskMapper.selectOne(new LambdaQueryWrapper<SuggestionTask>()
                    .eq(SuggestionTask::getSubmissionId, submissionId)
                    .eq(SuggestionTask::getWorkflowVersion, SuggestionV1Workflow.VERSION)
                    .last("LIMIT 1"));
            if (concurrent != null) return toVO(concurrent);
            throw exception;
        }
    }

    public SuggestionVO get(Long taskId, Long userId) {
        SuggestionTask task = requiredTask(taskId);
        checkMember(task.getTeamId(), userId);
        return toVO(task);
    }

    public List<SuggestionVO> listBySubmission(Long submissionId, Long userId) {
        SubmissionReviewDTO submission = requiredSubmission(submissionId);
        checkMember(submission.getTeamId(), userId);
        return taskMapper.selectList(new LambdaQueryWrapper<SuggestionTask>()
                        .eq(SuggestionTask::getSubmissionId, submissionId)
                        .orderByDesc(SuggestionTask::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    /** 兼容旧服务内调用，返回最新一份；公共 API 已改为返回完整历史。 */
    public SuggestionVO getBySubmission(Long submissionId, Long userId) {
        List<SuggestionVO> history = listBySubmission(submissionId, userId);
        BusinessException.throwIf(history.isEmpty(), SuggestionErrorCode.TASK_NOT_FOUND);
        return history.get(0);
    }

    public List<SuggestionVO> listTeam(Long teamId, Long userId) {
        checkMember(teamId, userId);
        return taskMapper.selectList(new LambdaQueryWrapper<SuggestionTask>()
                        .eq(SuggestionTask::getTeamId, teamId)
                        .orderByDesc(SuggestionTask::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public SuggestionVO retry(Long taskId, Long userId) {
        SuggestionTask task = requiredTask(taskId);
        checkMember(task.getTeamId(), userId);
        BusinessException.throwIf(!"FAILED".equals(task.getStatus()), SuggestionErrorCode.TASK_NOT_FAILED);
        int nextAttempt = (task.getAttemptNo() == null ? 1 : task.getAttemptNo()) + 1;
        LocalDateTime now = LocalDateTime.now();
        String nextIdempotencyKey = aiIdempotencyKey(taskId, nextAttempt);
        int updated = taskMapper.resetForRetry(taskId, now, nextIdempotencyKey);
        BusinessException.throwIf(updated == 0, SuggestionErrorCode.TASK_NOT_FAILED);
        task.setStatus("WAITING");
        task.setCurrentStage("PREPARING");
        task.setRetryCount(task.getRetryCount() + 1);
        task.setAttemptNo(nextAttempt);
        task.setAiIdempotencyKey(nextIdempotencyKey);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        task.setResultJson(null);
        task.setModelName(null);
        task.setAiCallId(null);
        enqueueReady(task, 0L);
        return toVO(task);
    }

    /** 由消息唤醒协调器调用；只有当前 fencing token 能推进任务。 */
    public void executeClaimed(Long taskId, String owner, String leaseToken) {
        SuggestionTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        LocalDateTime now = LocalDateTime.now();
        String idempotencyKey = aiIdempotencyKey(taskId,
                task.getAttemptNo() == null ? 1 : task.getAttemptNo());
        if (taskMapper.markRunning(taskId, owner, leaseToken, idempotencyKey, now,
                now.plusSeconds(workerProperties.getLeaseSeconds())) == 0) return;
        task.setStatus("RUNNING");
        task.setStartedAt(now);
        task.setLeaseOwner(owner);
        task.setLeaseToken(leaseToken);
        task.setAiIdempotencyKey(idempotencyKey);
        CorrelationSnapshot snapshot = CorrelationSnapshot.EMPTY
                .withTraceId(task.getTraceId())
                .withDomainTask(task.getId().toString(), task.getAttemptNo());
        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            if (SuggestionV1Workflow.VERSION.equals(task.getWorkflowVersion())) {
                processV1(task, leaseToken);
            } else if (GroundedSuggestionV2Workflow.VERSION.equals(task.getWorkflowVersion())) {
                processV2(task, leaseToken);
            } else {
                throw new IllegalArgumentException("未知建议工作流版本: " + task.getWorkflowVersion());
            }
        } catch (PendingEvidenceReview pending) {
            taskMapper.waitForEvidence(task.getId(), leaseToken, LocalDateTime.now().plusSeconds(10));
        } catch (Exception exception) {
            handleFailure(task, leaseToken, exception);
        }
    }

    private void processV1(SuggestionTask task, String leaseToken) throws Exception {
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        ReviewSummaryDTO review = requiredCompletedReviewBySubmission(task.getSubmissionId());
        ProblemContextDTO problem = requiredData(() -> problemFeignClient.getProblemContext(task.getProblemId()));
        validateTaskSource(task, submission, review, problem);
        SuggestionWorkflowResult result = v1Workflow.execute(task, submission, problem, review);
        complete(task, result, leaseToken);
    }

    private void processV2(SuggestionTask task, String leaseToken) throws Exception {
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        ReviewSummaryDTO eligibility = requiredCompletedReview(task.getEligibilityReviewTaskId());
        ProblemContextDTO problem = requiredData(() -> problemFeignClient.getProblemContext(task.getProblemId()));
        validateTaskSource(task, submission, eligibility, problem);

        updateStage(task, leaseToken, "PARSING");
        PaperParseDTO parse = requiredData(() -> reviewFeignClient.ensureParse(
                task.getSubmissionId(), task.getPaperParsingWorkflowVersion()));
        if (!("SUCCESS".equals(parse.getStatus()) || "PARTIAL_SUCCESS".equals(parse.getStatus()))) {
            throw new IllegalStateException("PDF 解析未产生可用产物");
        }
        task.setParseArtifactId(parse.getArtifactId());
        requireLease(taskMapper.saveParse(task.getId(), leaseToken, parse.getArtifactId()));

        task.setCurrentStage("PREPARING_REVIEW");
        ReviewEvidenceSnapshot reviewEvidence = resolveReviewEvidence(task, eligibility, leaseToken);
        task.setEvidenceReviewTaskId(reviewEvidence.evidenceReviewTaskId());
        task.setReviewWorkflowVersion(reviewEvidence.reviewWorkflowVersion());
        task.setReviewEvidenceProjectionVersion(reviewEvidence.projectionVersion());

        task.setCurrentStage("RETRIEVING");
        requireLease(taskMapper.saveReviewEvidence(task.getId(), leaseToken,
                reviewEvidence.evidenceReviewTaskId(), reviewEvidence.reviewWorkflowVersion(),
                reviewEvidence.projectionVersion()));
        KnowledgeRetrievalResultDTO knowledge = loadOrRetrieveKnowledge(task, problem, reviewEvidence);
        if (knowledge.getCitations() == null || knowledge.getCitations().isEmpty()) {
            throw new IllegalStateException("知识检索未返回可用于正式建议的参考资料");
        }
        task.setRetrievalRunId(knowledge.getRetrievalRunId());
        task.setKnowledgeSnapshotJson(objectMapper.writeValueAsString(knowledge));
        requireLease(taskMapper.saveKnowledge(task.getId(), leaseToken, task.getRetrievalRunId(),
                task.getKnowledgeSnapshotJson()));

        task.setCurrentStage("GENERATING");
        SuggestionWorkflowResult result = v2Workflow.execute(task, problem, parse, reviewEvidence, knowledge);
        updateStage(task, leaseToken, "VALIDATING");
        complete(task, result, leaseToken);
    }

    private ReviewEvidenceSnapshot resolveReviewEvidence(SuggestionTask task,
                                                         ReviewSummaryDTO eligibility,
                                                         String leaseToken) {
        if (evidenceProjector.isNativeV2(eligibility)) {
            return evidenceProjector.nativeV2(eligibility, eligibility);
        }
        if (evidenceProjector.hasStructuredLegacyEvidence(eligibility)) {
            return evidenceProjector.projectLegacy(eligibility);
        }
        Long evidenceTaskId = task.getEvidenceReviewTaskId();
        if (evidenceTaskId == null || Objects.equals(evidenceTaskId, eligibility.getTaskId())) {
            Result<Long> created = reviewFeignClient.createVersionedTask(task.getSubmissionId(),
                    task.getTeamId(), task.getProblemId(), "EVIDENCE_REVIEW_V2");
            if (created == null || !created.isSuccess() || created.getData() == null) {
                throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            evidenceTaskId = created.getData();
            task.setEvidenceReviewTaskId(evidenceTaskId);
            requireLease(taskMapper.saveEvidenceTask(task.getId(), leaseToken, evidenceTaskId));
        }
        ReviewSummaryDTO evidenceReview = requiredReview(evidenceTaskId);
        if ("FAILED".equals(evidenceReview.getStatus())) {
            throw new IllegalStateException("为历史分数补建的 V2 证据评审失败");
        }
        if (!"COMPLETED".equals(evidenceReview.getStatus())
                || evidenceReview.getResultJson() == null || evidenceReview.getResultJson().isBlank()) {
            throw new PendingEvidenceReview();
        }
        return evidenceProjector.nativeV2(eligibility, evidenceReview);
    }

    private KnowledgeRetrievalResultDTO loadOrRetrieveKnowledge(SuggestionTask task,
                                                                 ProblemContextDTO problem,
                                                                 ReviewEvidenceSnapshot evidence) {
        if (task.getKnowledgeSnapshotJson() != null && !task.getKnowledgeSnapshotJson().isBlank()) {
            try {
                return objectMapper.readValue(task.getKnowledgeSnapshotJson(),
                        KnowledgeRetrievalResultDTO.class);
            } catch (Exception exception) {
                throw new IllegalStateException("已锁定的知识检索快照无法读取", exception);
            }
        }
        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion(task.getRetrievalWorkflowVersion());
        request.setScene("PAPER_SUGGESTION");
        request.setTopK(8);
        request.setTokenBudget(4000);
        request.setQuery(buildKnowledgeQuery(problem, evidence));
        return requiredData(() -> knowledgeRetrievalFeignClient.retrieve(request));
    }

    private String buildKnowledgeQuery(ProblemContextDTO problem, ReviewEvidenceSnapshot evidence) {
        StringBuilder query = new StringBuilder("数学建模论文改进。题目：")
                .append(problem.getTitle()).append("。需要解决的评审发现：");
        evidence.findings().stream().filter(item -> "ISSUE".equals(item.type())).limit(8)
                .forEach(item -> query.append('[').append(item.category()).append("] ")
                        .append(item.statement()).append('；'));
        return query.substring(0, Math.min(query.length(), 3800));
    }

    private void complete(SuggestionTask task, SuggestionWorkflowResult result, String leaseToken) {
        LocalDateTime finishedAt = LocalDateTime.now();
        requireLease(taskMapper.complete(task.getId(), leaseToken, result.resultJson(),
                result.modelName(), result.aiCallId(), finishedAt));
        task.setStatus("COMPLETED");
        task.setCurrentStage("COMPLETED");
        task.setResultJson(result.resultJson());
        task.setModelName(result.modelName());
        task.setAiCallId(result.aiCallId());
        task.setFinishedAt(finishedAt);
        task.setErrorMessage(null);
    }

    private void handleFailure(SuggestionTask task, String leaseToken, Exception exception) {
        if (exception instanceof LeaseLostException) return;
        String error = truncate(exception.getMessage());
        if (exception instanceof AiClientException aiError) {
            if (aiError.getCode() == 51212) {
                taskMapper.scheduleSameAttempt(task.getId(), leaseToken,
                        LocalDateTime.now().plusSeconds(10), "AI_PENDING", error);
                return;
            }
            if (aiError.getCode() == 51213 || aiError.getCode() == 50002) {
                taskMapper.markTerminalFailure(task.getId(), leaseToken,
                        "UNKNOWN", "AI_UNKNOWN", "AI 上游结果未知，禁止自动重试");
                return;
            }
            taskMapper.markTerminalFailure(task.getId(), leaseToken,
                    "FAILED", "AI_FAILED", error);
            return;
        }
        int maxAttempts = task.getMaxAttempts() == null
                ? workerProperties.getMaxAttempts() : task.getMaxAttempts();
        int attempt = task.getAttemptNo() == null ? 1 : task.getAttemptNo();
        if (isTransientDependency(exception) && attempt < maxAttempts) {
            int nextAttempt = attempt + 1;
            taskMapper.scheduleRetry(task.getId(), leaseToken,
                    LocalDateTime.now().plusSeconds(retryDelaySeconds(attempt)),
                    "DEPENDENCY_TRANSIENT", error, aiIdempotencyKey(task.getId(), nextAttempt));
            return;
        }
        taskMapper.markTerminalFailure(task.getId(), leaseToken,
                "FAILED", "WORKFLOW_FAILED", error);
        log.error("论文建议任务失败 taskId={}, attempt={}", task.getId(), attempt, exception);
    }

    private long retryDelaySeconds(int failedAttempt) {
        return switch (failedAttempt) {
            case 1 -> 10L;
            case 2 -> 60L;
            default -> 300L;
        };
    }

    private boolean isTransientDependency(Exception exception) {
        return exception instanceof BusinessException businessException
                && businessException.getCode() == SuggestionErrorCode.DEPENDENCY_UNAVAILABLE.getCode();
    }

    private void updateStage(SuggestionTask task, String leaseToken, String stage) {
        requireLease(taskMapper.updateStage(task.getId(), leaseToken, stage));
        task.setCurrentStage(stage);
    }

    private void requireLease(int updated) {
        if (updated == 0) throw new LeaseLostException();
    }

    public long count() { return taskMapper.selectCount(null); }

    public List<SuggestionTaskSummaryDTO> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return taskMapper.selectList(new LambdaQueryWrapper<SuggestionTask>()
                        .orderByDesc(SuggestionTask::getCreateTime).last("LIMIT " + safeLimit))
                .stream().map(this::toSummary).toList();
    }

    private SubmissionReviewDTO requiredSubmission(Long submissionId) {
        return requiredData(() -> submissionFeignClient.getForReview(submissionId));
    }

    private ReviewSummaryDTO requiredCompletedReview(Long taskId) {
        ReviewSummaryDTO review = requiredReview(taskId);
        BusinessException.throwIf(!"COMPLETED".equals(review.getStatus())
                        || review.getResultJson() == null || review.getResultJson().isBlank(),
                SuggestionErrorCode.REVIEW_NOT_READY);
        return review;
    }

    private ReviewSummaryDTO requiredCompletedReviewBySubmission(Long submissionId) {
        try {
            Result<ReviewSummaryDTO> response = reviewFeignClient.getBySubmission(submissionId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException(SuggestionErrorCode.REVIEW_NOT_READY);
            }
            ReviewSummaryDTO review = response.getData();
            BusinessException.throwIf(!"COMPLETED".equals(review.getStatus())
                            || review.getResultJson() == null || review.getResultJson().isBlank(),
                    SuggestionErrorCode.REVIEW_NOT_READY);
            return review;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private ReviewSummaryDTO requiredReview(Long taskId) {
        try {
            Result<ReviewSummaryDTO> response = reviewFeignClient.getByTask(taskId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new BusinessException(SuggestionErrorCode.REVIEW_NOT_READY);
            }
            return response.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void checkMember(Long teamId, Long userId) {
        Result<List<Long>> response;
        try {
            response = teamFeignClient.getMemberIds(teamId);
        } catch (RuntimeException exception) {
            throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        BusinessException.throwIf(response == null || !response.isSuccess() || response.getData() == null,
                SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
        BusinessException.throwIf(!response.getData().contains(userId), SuggestionErrorCode.NOT_TEAM_MEMBER);
    }

    private void validateSource(SubmissionReviewDTO submission, ReviewSummaryDTO review) {
        BusinessException.throwIf(!Objects.equals(submission.getId(), review.getSubmissionId())
                        || !Objects.equals(submission.getTeamId(), review.getTeamId())
                        || !Objects.equals(submission.getProblemId(), review.getProblemId()),
                SuggestionErrorCode.SOURCE_DATA_INVALID);
    }

    private void validateTaskSource(SuggestionTask task, SubmissionReviewDTO submission,
                                    ReviewSummaryDTO review, ProblemContextDTO problem) {
        validateSource(submission, review);
        Long expectedReviewTaskId = SuggestionV1Workflow.VERSION.equals(task.getWorkflowVersion())
                ? task.getReviewTaskId() : task.getEligibilityReviewTaskId();
        BusinessException.throwIf(!Objects.equals(task.getSubmissionId(), submission.getId())
                        || !Objects.equals(task.getTeamId(), submission.getTeamId())
                        || !Objects.equals(task.getProblemId(), submission.getProblemId())
                        || !Objects.equals(expectedReviewTaskId, review.getTaskId())
                        || !Objects.equals(task.getProblemId(), problem.getId()),
                SuggestionErrorCode.SOURCE_DATA_INVALID);
    }

    private void validateClientRequestId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("clientRequestId 必须是 8 到 64 位安全标识");
        }
    }

    private void assertAdmissionAvailable() {
        long count = taskMapper.countActiveBacklog();
        LocalDateTime oldest = taskMapper.selectOldestDue(LocalDateTime.now());
        boolean oldestSevere = oldest != null && Duration.between(oldest, LocalDateTime.now()).getSeconds()
                >= workerProperties.getSevereOldestWaitingSeconds();
        BusinessException.throwIf(count >= workerProperties.getSevereBacklogCount() || oldestSevere,
                SuggestionErrorCode.SERVICE_BUSY);
    }

    private void enqueueReady(SuggestionTask task, long bucket) {
        if (readyMessageService != null) readyMessageService.enqueue(task, bucket);
    }

    public static String aiIdempotencyKey(Long taskId, int attemptNo) {
        return "suggestion:task:" + taskId + ":attempt:" + attemptNo;
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }

    private static SuggestionWorkerProperties defaultWorkerProperties() {
        return new SuggestionWorkerProperties();
    }

    private SuggestionTask findByClientRequest(Long userId, String clientRequestId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<SuggestionTask>()
                .eq(SuggestionTask::getRequestedByUserId, userId)
                .eq(SuggestionTask::getClientRequestId, clientRequestId)
                .last("LIMIT 1"));
    }

    private SuggestionTask validateIdempotentPayload(SuggestionTask existing,
                                                      SubmissionReviewDTO submission,
                                                      ReviewSummaryDTO review) {
        if (!Objects.equals(existing.getSubmissionId(), submission.getId())
                || !Objects.equals(existing.getEligibilityReviewTaskId(), review.getTaskId())
                || !GroundedSuggestionV2Workflow.VERSION.equals(existing.getWorkflowVersion())) {
            throw new IllegalArgumentException("clientRequestId 已用于不同的建议生成请求");
        }
        return existing;
    }

    private SuggestionTask requiredTask(Long taskId) {
        SuggestionTask task = taskMapper.selectById(taskId);
        BusinessException.throwIf(task == null, SuggestionErrorCode.TASK_NOT_FOUND);
        return task;
    }

    private SuggestionVO toVO(SuggestionTask task) {
        Object output = null;
        KnowledgeViewSnapshot knowledge = readKnowledgeSnapshot(task);
        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            try {
                output = GroundedSuggestionV2Workflow.VERSION.equals(task.getWorkflowVersion())
                        ? objectMapper.readValue(task.getResultJson(), GroundedSuggestionV2Output.class)
                        : objectMapper.readValue(task.getResultJson(), SuggestionV1Output.class);
            } catch (Exception exception) {
                throw new IllegalStateException("已保存的论文建议结果无法解析", exception);
            }
        }
        return SuggestionVO.builder()
                .taskId(task.getId()).submissionId(task.getSubmissionId())
                .teamId(task.getTeamId()).problemId(task.getProblemId())
                .reviewTaskId(task.getReviewTaskId())
                .eligibilityReviewTaskId(task.getEligibilityReviewTaskId())
                .evidenceReviewTaskId(task.getEvidenceReviewTaskId())
                .parseArtifactId(task.getParseArtifactId())
                .workflowVersion(task.getWorkflowVersion())
                .reviewWorkflowVersion(task.getReviewWorkflowVersion())
                .reviewEvidenceProjectionVersion(task.getReviewEvidenceProjectionVersion())
                .paperParsingWorkflowVersion(task.getPaperParsingWorkflowVersion())
                .retrievalRunId(task.getRetrievalRunId())
                .retrievalWorkflowVersion(task.getRetrievalWorkflowVersion())
                .knowledgeIndexVersion(knowledge.indexVersion())
                .knowledgeManifestVersion(knowledge.manifestVersion())
                .knowledgeSourceVersion(knowledge.sourceVersion())
                .resultSchemaVersion(task.getResultSchemaVersion())
                .status(task.getStatus()).currentStage(task.getCurrentStage())
                .retryCount(task.getRetryCount()).attemptNo(task.getAttemptNo())
                .errorMessage(task.getErrorMessage()).result(output)
                .knowledgeCitations(knowledge.citations())
                .modelName(task.getModelName()).aiCallId(task.getAiCallId())
                .createTime(task.getCreateTime()).startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt()).build();
    }

    private KnowledgeViewSnapshot readKnowledgeSnapshot(SuggestionTask task) {
        if (task.getKnowledgeSnapshotJson() == null || task.getKnowledgeSnapshotJson().isBlank()) {
            return new KnowledgeViewSnapshot(null, null, null, List.of());
        }
        try {
            KnowledgeRetrievalResultDTO snapshot = objectMapper.readValue(
                    task.getKnowledgeSnapshotJson(), KnowledgeRetrievalResultDTO.class);
            List<SuggestionKnowledgeCitationVO> citations = snapshot.getCitations() == null
                    ? List.of() : snapshot.getCitations().stream().map(item -> new SuggestionKnowledgeCitationVO(
                    item.getCitationId(), item.getTitle(), item.getSourcePath(), item.getContentHash(),
                    item.getAuthorityLevel(), item.getApplicability())).toList();
            return new KnowledgeViewSnapshot(snapshot.getIndexVersion(), snapshot.getManifestVersion(),
                    snapshot.getSourceVersion(), citations);
        } catch (Exception exception) {
            throw new IllegalStateException("已保存的知识引用快照无法解析", exception);
        }
    }

    private record KnowledgeViewSnapshot(String indexVersion, String manifestVersion,
                                         String sourceVersion,
                                         List<SuggestionKnowledgeCitationVO> citations) {}

    private SuggestionTaskSummaryDTO toSummary(SuggestionTask task) {
        return new SuggestionTaskSummaryDTO(task.getId(), task.getSubmissionId(), task.getTeamId(),
                task.getProblemId(), task.getStatus(), task.getWorkflowVersion(), task.getModelName(),
                task.getAiCallId(), task.getErrorMessage(), task.getCreateTime(), task.getFinishedAt());
    }

    private <T> T requiredData(Supplier<Result<T>> call) {
        try {
            Result<T> result = call.get();
            BusinessException.throwIf(result == null || !result.isSuccess() || result.getData() == null,
                    SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
            return result.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.substring(0, Math.min(message.length(), 500));
    }

    private static final class PendingEvidenceReview extends RuntimeException {
    }

    private static final class LeaseLostException extends RuntimeException {
    }
}
