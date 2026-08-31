package com.leetmodel.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.leetmodel.suggestion.dto.SuggestionCreateRequest;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.enums.SuggestionErrorCode;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
        this.objectMapper = objectMapper;
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
                null, v1Workflow, null, null, objectMapper);
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
        task.setNextRunAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
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
        task.setRetryCount(0); task.setAttemptNo(1); task.setNextRunAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
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
        int updated = taskMapper.resetForRetry(taskId, LocalDateTime.now());
        BusinessException.throwIf(updated == 0, SuggestionErrorCode.TASK_NOT_FAILED);
        task.setStatus("WAITING");
        task.setCurrentStage("PREPARING");
        task.setRetryCount(task.getRetryCount() + 1);
        task.setAttemptNo((task.getAttemptNo() == null ? 1 : task.getAttemptNo()) + 1);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        task.setResultJson(null);
        task.setModelName(null);
        task.setAiCallId(null);
        return toVO(task);
    }

    @Scheduled(fixedDelayString = "${suggestion.worker.delay-ms:2000}")
    public void processNext() {
        LocalDateTime now = LocalDateTime.now();
        SuggestionTask task = taskMapper.selectNextWaiting(now);
        if (task == null || taskMapper.claim(task.getId(), now) == 0) return;
        task.setStatus("RUNNING");
        task.setStartedAt(now);
        try {
            if (SuggestionV1Workflow.VERSION.equals(task.getWorkflowVersion())) {
                processV1(task);
            } else if (GroundedSuggestionV2Workflow.VERSION.equals(task.getWorkflowVersion())) {
                processV2(task);
            } else {
                throw new IllegalArgumentException("未知建议工作流版本: " + task.getWorkflowVersion());
            }
        } catch (PendingEvidenceReview pending) {
            task.setStatus("WAITING");
            task.setCurrentStage("PREPARING_REVIEW");
            task.setNextRunAt(LocalDateTime.now().plusSeconds(5));
            task.setStartedAt(null);
            task.setErrorMessage(null);
            taskMapper.updateById(task);
        } catch (Exception exception) {
            log.error("论文建议任务失败 taskId={}", task.getId(), exception);
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(truncate(exception.getMessage()));
            taskMapper.updateById(task);
        }
    }

    private void processV1(SuggestionTask task) throws Exception {
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        ReviewSummaryDTO review = requiredCompletedReviewBySubmission(task.getSubmissionId());
        ProblemContextDTO problem = requiredData(() -> problemFeignClient.getProblemContext(task.getProblemId()));
        validateTaskSource(task, submission, review, problem);
        SuggestionWorkflowResult result = v1Workflow.execute(task, submission, problem, review);
        complete(task, result);
    }

    private void processV2(SuggestionTask task) throws Exception {
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        ReviewSummaryDTO eligibility = requiredCompletedReview(task.getEligibilityReviewTaskId());
        ProblemContextDTO problem = requiredData(() -> problemFeignClient.getProblemContext(task.getProblemId()));
        validateTaskSource(task, submission, eligibility, problem);

        task.setCurrentStage("PARSING");
        taskMapper.updateById(task);
        PaperParseDTO parse = requiredData(() -> reviewFeignClient.ensureParse(
                task.getSubmissionId(), task.getPaperParsingWorkflowVersion()));
        if (!("SUCCESS".equals(parse.getStatus()) || "PARTIAL_SUCCESS".equals(parse.getStatus()))) {
            throw new IllegalStateException("PDF 解析未产生可用产物");
        }
        task.setParseArtifactId(parse.getArtifactId());

        task.setCurrentStage("PREPARING_REVIEW");
        taskMapper.updateById(task);
        ReviewEvidenceSnapshot reviewEvidence = resolveReviewEvidence(task, eligibility);
        task.setEvidenceReviewTaskId(reviewEvidence.evidenceReviewTaskId());
        task.setReviewWorkflowVersion(reviewEvidence.reviewWorkflowVersion());
        task.setReviewEvidenceProjectionVersion(reviewEvidence.projectionVersion());

        task.setCurrentStage("RETRIEVING");
        taskMapper.updateById(task);
        KnowledgeRetrievalResultDTO knowledge = loadOrRetrieveKnowledge(task, problem, reviewEvidence);
        if (knowledge.getCitations() == null || knowledge.getCitations().isEmpty()) {
            throw new IllegalStateException("知识检索未返回可用于正式建议的参考资料");
        }
        task.setRetrievalRunId(knowledge.getRetrievalRunId());
        task.setKnowledgeSnapshotJson(objectMapper.writeValueAsString(knowledge));

        task.setCurrentStage("GENERATING");
        taskMapper.updateById(task);
        SuggestionWorkflowResult result = v2Workflow.execute(task, problem, parse, reviewEvidence, knowledge);
        task.setCurrentStage("VALIDATING");
        taskMapper.updateById(task);
        complete(task, result);
    }

    private ReviewEvidenceSnapshot resolveReviewEvidence(SuggestionTask task,
                                                         ReviewSummaryDTO eligibility) {
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
            taskMapper.updateById(task);
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

    private void complete(SuggestionTask task, SuggestionWorkflowResult result) {
        task.setStatus("COMPLETED");
        task.setCurrentStage("COMPLETED");
        task.setResultJson(result.resultJson());
        task.setModelName(result.modelName());
        task.setAiCallId(result.aiCallId());
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        taskMapper.updateById(task);
    }

    @Scheduled(fixedDelayString = "${suggestion.worker.recovery-delay-ms:60000}")
    public void recoverStaleTasks() {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.recoverStale(now.minusMinutes(10), now);
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
}
