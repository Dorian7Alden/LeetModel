package com.leetmodel.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.ReviewExperimentResultDTO;
import com.leetmodel.common.api.dto.ReviewVersionDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.AiWorkflowVersionDTO;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.ai.client.AiClientException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.entity.ReviewV1Result;
import com.leetmodel.review.entity.ReviewVersion;
import com.leetmodel.review.entity.ReviewV2Result;
import com.leetmodel.review.enums.ReviewErrorCode;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewVersionMapper;
import com.leetmodel.review.mapper.ReviewV2ResultMapper;
import com.leetmodel.review.vo.ReviewVO;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowRegistry;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import com.leetmodel.review.workflow.v1.BasicReviewV1Workflow;
import com.leetmodel.review.workflow.v2.EvidenceReviewV2Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j @Service
public class ReviewService {
    /** 旧内部入口保持 V1 语义；新提交由 submission-service 显式选择 V2。 */
    public static final String WORKFLOW_VERSION = BasicReviewV1Workflow.VERSION_CODE;
    private final ReviewTaskMapper taskMapper;
    private final ReviewV1ResultMapper resultMapper;
    private final ReviewV2ResultMapper v2ResultMapper;
    private final ReviewVersionMapper versionMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final ReviewWorkflowRegistry workflowRegistry;
    private final ReviewTaskLogService logService;
    private final ReviewResultPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    @Value("${review.worker.max-attempts:3}")
    private int configuredMaxAttempts = 3;

    @Autowired
    public ReviewService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper,
                         ReviewV2ResultMapper v2ResultMapper,
                         ReviewVersionMapper versionMapper, SubmissionFeignClient submissionFeignClient,
                         TeamFeignClient teamFeignClient, ReviewWorkflowRegistry workflowRegistry,
                         ReviewTaskLogService logService, ReviewResultPersistenceService persistenceService,
                         ObjectMapper objectMapper) {
        this.taskMapper = taskMapper; this.resultMapper = resultMapper; this.v2ResultMapper = v2ResultMapper;
        this.versionMapper = versionMapper;
        this.submissionFeignClient = submissionFeignClient; this.teamFeignClient = teamFeignClient;
        this.workflowRegistry = workflowRegistry; this.logService = logService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    /** 供既有单元测试和历史嵌入调用保留的 V1 构造契约。 */
    public ReviewService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper,
                         ReviewVersionMapper versionMapper, SubmissionFeignClient submissionFeignClient,
                         TeamFeignClient teamFeignClient, ReviewWorkflowRegistry workflowRegistry,
                         ReviewTaskLogService logService, ReviewResultPersistenceService persistenceService,
                         ObjectMapper objectMapper) {
        this(taskMapper, resultMapper, null, versionMapper, submissionFeignClient, teamFeignClient,
                workflowRegistry, logService, persistenceService, objectMapper);
    }

    @Transactional
    public Long createTask(Long submissionId, Long teamId, Long problemId) {
        return createTask(submissionId, teamId, problemId, WORKFLOW_VERSION);
    }

    @Transactional
    public Long createTask(Long submissionId, Long teamId, Long problemId, String workflowVersion) {
        return createTask(submissionId, teamId, problemId, workflowVersion, currentTraceId());
    }

    /**
     * 按领域唯一键幂等创建正式评审任务，并保存消息链路 TraceId。
     *
     * @param submissionId 提交标识
     * @param teamId 队伍标识
     * @param problemId 题目标识
     * @param workflowVersion 工作流版本
     * @param traceId 消息链路标识
     * @return 新建或既有任务标识
     */
    @Transactional
    public Long createTask(Long submissionId, Long teamId, Long problemId,
                           String workflowVersion, String traceId) {
        ReviewTask existing = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getSubmissionId, submissionId)
                .eq(ReviewTask::getWorkflowVersion, workflowVersion));
        if (existing != null) return existing.getId();
        ReviewWorkflow workflow = workflowRegistry.required(workflowVersion);
        ReviewTask task = new ReviewTask();
        task.setSubmissionId(submissionId); task.setVersionId(workflow.versionId());
        task.setTeamId(teamId); task.setProblemId(problemId); task.setStatus("WAITING"); task.setPriority(100);
        task.setTraceId(traceId);
        task.setWorkflowVersion(workflow.versionCode()); task.setPromptSnapshot(workflow.currentPrompt());
        task.setRetryCount(0); task.setAttemptNo(1); task.setMaxAttempts(configuredMaxAttempts);
        task.setRecoveryCount(0);
        task.setAiIdempotencyKey(aiIdempotencyKey(submissionId, workflow.versionCode(), 1));
        task.setNextRunAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
            return task.getId();
        } catch (DuplicateKeyException exception) {
            ReviewTask concurrent = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                    .eq(ReviewTask::getSubmissionId, submissionId)
                    .eq(ReviewTask::getWorkflowVersion, workflowVersion));
            if (concurrent != null) return concurrent.getId();
            throw exception;
        }
    }

    public ReviewVO getTask(Long taskId, Long userId) {
        ReviewTask task = requiredTask(taskId);
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        checkMember(submission.getTeamId(), userId);
        return toVO(task, storedResult(task));
    }

    public List<ReviewVO> listTeamResults(Long teamId, Long userId) {
        checkMember(teamId, userId);
        return taskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getTeamId, teamId).orderByDesc(ReviewTask::getCreateTime))
                .stream().map(task -> toVO(task, storedResult(task))).toList();
    }

    /**
     * 按提交 ID 查询最新评审摘要。
     * @param submissionId 提交 ID
     * @return 评审摘要
     */
    public ReviewSummaryDTO getSummaryBySubmission(Long submissionId) {
        ReviewTask task = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getSubmissionId, submissionId)
                .eq(ReviewTask::getStatus, "COMPLETED")
                .orderByDesc(ReviewTask::getCreateTime)
                .last("LIMIT 1"));
        BusinessException.throwIf(task == null, ReviewErrorCode.TASK_NOT_FOUND);
        return toSummary(task, storedResult(task));
    }

    public ReviewSummaryDTO getSummaryByTask(Long taskId) {
        ReviewTask task = requiredTask(taskId);
        return toSummary(task, storedResult(task));
    }

    /**
     * 查询已完成且已产生结果的评审摘要。
     * @param problemId 可选题目 ID
     * @return 评审摘要列表
     */
    public List<ReviewSummaryDTO> listCompletedSummaries(Long problemId) {
        List<ReviewSummaryDTO> summaries = new java.util.ArrayList<>();
        LambdaQueryWrapper<ReviewV1Result> v1Query = new LambdaQueryWrapper<>();
        if (problemId != null) v1Query.eq(ReviewV1Result::getProblemId, problemId);
        v1Query.orderByDesc(ReviewV1Result::getCreateTime);
        resultMapper.selectList(v1Query).forEach(result -> {
            ReviewTask task = taskMapper.selectById(result.getTaskId());
            if (task != null) summaries.add(toSummary(task, new StoredResult(result.getScore(),
                    result.getResultJson(), result.getModelName(), result.getAiCallId())));
        });
        if (v2ResultMapper != null) {
            LambdaQueryWrapper<ReviewV2Result> v2Query = new LambdaQueryWrapper<>();
            if (problemId != null) v2Query.eq(ReviewV2Result::getProblemId, problemId);
            v2Query.orderByDesc(ReviewV2Result::getCreateTime);
            v2ResultMapper.selectList(v2Query).forEach(result -> {
                ReviewTask task = taskMapper.selectById(result.getTaskId());
                if (task != null) summaries.add(toSummary(task, new StoredResult(result.getScore(),
                        result.getResultJson(), result.getModelName(), result.getAiCallId())));
            });
        }
        return summaries.stream().sorted(java.util.Comparator.comparing(
                        ReviewSummaryDTO::getFinishedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 获取评审任务数量。
     * @return 评审任务数量
     */
    public long count() {
        return taskMapper.selectCount(null);
    }

    /** 管理聚合使用的最近评审任务，包含等待、运行和失败状态。 */
    public List<ReviewSummaryDTO> listRecentSummaries(int limit) {
        return taskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                        .orderByDesc(ReviewTask::getCreateTime).last("LIMIT " + limit))
                .stream().map(task -> toSummary(task, storedResult(task))).toList();
    }

    /**
     * 查询当前登记的评审版本，供管理端展示和评价任务校验。
     */
    public List<ReviewVersionDTO> listVersions() {
        return versionMapper.selectList(new LambdaQueryWrapper<ReviewVersion>()
                        .orderByAsc(ReviewVersion::getId))
                .stream().map(version -> new ReviewVersionDTO(
                        version.getId(), version.getVersionCode(), version.getName(),
                        version.getDescription(), version.getProcessSummary(), version.getStatus()))
                .toList();
    }

    /**
     * 返回 REVIEW 功能及全部已发布版本。禁用版本仍保留在目录中供历史解释。
     */
    public AiFeatureDefinitionDTO getFeatureDefinition() {
        List<AiWorkflowVersionDTO> versions = versionMapper.selectList(
                        new LambdaQueryWrapper<ReviewVersion>().orderByAsc(ReviewVersion::getId))
                .stream()
                .map(version -> new AiWorkflowVersionDTO(
                        version.getVersionCode(), version.getName(), version.getStatus(),
                        "REVIEW_SUBMISSION_V1",
                        version.getFinalContractVersion(),
                        EvidenceReviewV2Workflow.VERSION_CODE.equals(version.getVersionCode())
                                ? "输入锁定完整题面与 PAPER_DOCUMENT_V1；输出统一总分、评分说明、题目覆盖和页码证据"
                                : "输入为 submission-service 的不可变提交；输出保持统一评审总分与版本结果 JSON"))
                .toList();
        return new AiFeatureDefinitionDTO(
                "REVIEW", "AI 论文评审", "ai-review-service",
                List.of("SUBMISSION_SNAPSHOT", "PROBLEM_CONTEXT", "PAPER_DOCUMENT_V1"),
                List.of("RUN_SUCCESS", "DURATION_MS", "SCORE_STABILITY", "EVIDENCE_VALIDITY"), versions);
    }

    /**
     * 执行一次不写入正式评审任务的版本实验，供质量评价服务使用。
     *
     * @param submissionId 已有提交标识
     * @param workflowVersion 待评价评审版本
     * @return 隔离实验结果或分类失败
     */
    public ReviewExperimentResultDTO runExperiment(Long submissionId, String workflowVersion) {
        return runExperiment(submissionId, workflowVersion, null,
                "MODEL_CFG_REVIEW_MULTIMODAL_0001");
    }

    private ReviewExperimentResultDTO runExperiment(Long submissionId, String workflowVersion,
                                                     String experimentRunId, String modelConfigVersion) {
        return runExperiment(submissionId, workflowVersion, experimentRunId, modelConfigVersion,
                null, null);
    }

    private ReviewExperimentResultDTO runExperiment(Long submissionId, String workflowVersion,
                                                     String experimentRunId, String modelConfigVersion,
                                                     String evaluationTaskId, String idempotencyKey) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            ReviewWorkflow workflow = workflowRegistry.required(workflowVersion);
            SubmissionReviewDTO submission = requiredSubmission(submissionId);
            ReviewTask transientTask = new ReviewTask();
            transientTask.setSubmissionId(submissionId);
            transientTask.setTeamId(submission.getTeamId());
            transientTask.setProblemId(submission.getProblemId());
            transientTask.setVersionId(workflow.versionId());
            transientTask.setWorkflowVersion(workflow.versionCode());
            transientTask.setPromptSnapshot(workflow.currentPrompt());
            transientTask.setAttemptNo(1);
            transientTask.setExperimentRunId(experimentRunId);
            transientTask.setEvaluationTaskId(evaluationTaskId);
            transientTask.setExperimentIdempotencyKey(idempotencyKey);
            transientTask.setModelExecutionConfigVersion(modelConfigVersion);
            ReviewWorkflowResult result = workflow.execute(transientTask, submission);
            return new ReviewExperimentResultDTO(
                    submissionId, submission.getProblemId(), workflow.versionCode(), "SUCCEEDED", null,
                    result.score(), result.resultJson(), result.modelName(), result.aiCallId(),
                    Duration.between(startedAt, LocalDateTime.now()).toMillis(), null);
        } catch (Exception exception) {
            log.warn("隔离评审实验失败 submissionId={}, workflowVersion={}, message={}",
                    submissionId, workflowVersion, exception.getMessage());
            String failureType = classifyExperimentFailure(exception);
            String status = "PENDING".equals(failureType) ? "PENDING"
                    : "UNKNOWN".equals(failureType) ? "UNKNOWN" : "FAILED";
            return new ReviewExperimentResultDTO(
                    submissionId, null, workflowVersion, status,
                    "PENDING".equals(failureType) ? null : failureType,
                    null, null, null, null,
                    Duration.between(startedAt, LocalDateTime.now()).toMillis(),
                    experimentErrorMessage(failureType));
        }
    }

    /** 使用通用契约执行瞬态评审，且不写入正式评审表。 */
    public AiExperimentResultDTO runExperiment(AiExperimentRequestDTO request) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            boolean evidenceV2 = EvidenceReviewV2Workflow.VERSION_CODE.equals(
                    request.getWorkflowVersion());
            String requiredModelConfig = evidenceV2
                    ? "MODEL_CFG_REVIEW_TEXT_0002" : "MODEL_CFG_REVIEW_MULTIMODAL_0001";
            if (!"REVIEW".equals(request.getFeatureCode())
                    || !"SUBMISSION_REFERENCE".equals(request.getSample().getSampleType())
                    || !"REVIEW_SUBMISSION_V1".equals(request.getSample().getSchemaVersion())
                    || !requiredModelConfig.equals(request.getModelExecutionConfigVersion())
                    || request.getRagIndexVersion() != null
                    || !"P3".equals(request.getPriority())
                    || !experimentContextComplete(request)) {
                throw new IllegalArgumentException("评审实验配置与 REVIEW 契约不匹配");
            }
            ReviewVersion version = versionMapper.selectOne(new LambdaQueryWrapper<ReviewVersion>()
                    .eq(ReviewVersion::getVersionCode, request.getWorkflowVersion())
                    .last("LIMIT 1"));
            if (version == null || !"ENABLED".equals(version.getStatus())) {
                throw new IllegalArgumentException("评审版本不可用于新实验");
            }
            Long submissionId = objectMapper.readTree(request.getSample().getPayloadJson())
                    .required("submissionId").asLong();
            if (submissionId <= 0) throw new IllegalArgumentException("submissionId 必须为正整数");
            ReviewExperimentResultDTO legacy = runExperiment(submissionId,
                    request.getWorkflowVersion(), request.getExperimentRunId(),
                    request.getModelExecutionConfigVersion(), request.getEvaluationTaskId(),
                    request.getIdempotencyKey());
            return new AiExperimentResultDTO(request.getExperimentRunId(), "REVIEW",
                    legacy.getWorkflowVersion(), request.getModelExecutionConfigVersion(), null,
                    legacy.getStatus(), legacy.getFailureType(), "SCORE_V1", legacy.getResultJson(),
                    "REVIEW_RUN_METRICS_V1", legacy.getScore() == null ? null
                    : objectMapper.writeValueAsString(java.util.Map.of("score", legacy.getScore())),
                    legacy.getModelName(), legacy.getAiCallId(), legacy.getDurationMs(),
                    legacy.getErrorMessage());
        } catch (Exception exception) {
            return new AiExperimentResultDTO(request.getExperimentRunId(), request.getFeatureCode(),
                    request.getWorkflowVersion(), request.getModelExecutionConfigVersion(),
                    request.getRagIndexVersion(), "FAILED", "CONFIGURATION", null, null,
                    null, null, null, null,
                    Duration.between(startedAt, LocalDateTime.now()).toMillis(),
                    "评审实验请求不符合已发布契约");
        }
    }

    private boolean experimentContextComplete(AiExperimentRequestDTO request) {
        boolean none = request.getEvaluationTaskId() == null && request.getSlotKey() == null
                && request.getAttemptNo() == null && request.getIdempotencyKey() == null;
        boolean all = request.getEvaluationTaskId() != null && !request.getEvaluationTaskId().isBlank()
                && request.getSlotKey() != null && !request.getSlotKey().isBlank()
                && request.getAttemptNo() != null && request.getAttemptNo() > 0
                && request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank();
        return none || all;
    }

    @Transactional
    public ReviewVO retry(Long taskId, Long userId) {
        ReviewTask task = requiredTask(taskId);
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        checkMember(submission.getTeamId(), userId);
        BusinessException.throwIf(!"FAILED".equals(task.getStatus()), ReviewErrorCode.TASK_NOT_FAILED);
        task.setStatus("WAITING"); task.setRetryCount(task.getRetryCount() + 1);
        task.setAttemptNo(task.getAttemptNo() + 1); task.setNextRunAt(LocalDateTime.now());
        task.setStartedAt(null); task.setFinishedAt(null); task.setErrorMessage(null);
        task.setFailureType(null);
        task.setAiIdempotencyKey(aiIdempotencyKey(
                task.getSubmissionId(), task.getWorkflowVersion(), task.getAttemptNo()));
        taskMapper.resetForRetry(task);
        return toVO(task, null);
    }

    private ReviewTask requiredTask(Long id) {
        ReviewTask task = taskMapper.selectById(id);
        BusinessException.throwIf(task == null, ReviewErrorCode.TASK_NOT_FOUND);
        return task;
    }
    private SubmissionReviewDTO requiredSubmission(Long id) {
        Result<SubmissionReviewDTO> response = submissionFeignClient.getForReview(id);
        BusinessException.throwIf(response == null || !response.isSuccess() || response.getData() == null,
                ReviewErrorCode.DEPENDENCY_UNAVAILABLE);
        return response.getData();
    }
    private void checkMember(Long teamId, Long userId) {
        Result<List<Long>> response = teamFeignClient.getMemberIds(teamId);
        BusinessException.throwIf(response == null || !response.isSuccess() || response.getData() == null
                || !response.getData().contains(userId), ReviewErrorCode.NOT_TEAM_MEMBER);
    }
    private ReviewVO toVO(ReviewTask task, StoredResult result) {
        ReviewVersion version = versionMapper.selectById(task.getVersionId());
        return ReviewVO.builder().taskId(task.getId()).submissionId(task.getSubmissionId()).status(task.getStatus())
                .workflowVersion(task.getWorkflowVersion())
                .versionName(version == null ? task.getWorkflowVersion() : version.getName())
                .versionDescription(version == null ? null : version.getDescription())
                .processSummary(version == null ? null : version.getProcessSummary())
                .retryCount(task.getRetryCount()).attemptNo(task.getAttemptNo()).errorMessage(task.getErrorMessage())
                .finishedAt(task.getFinishedAt()).score(result == null ? null : result.score())
                .resultJson(result == null ? null : result.resultJson())
                .modelName(result == null ? null : result.modelName())
                .aiCallId(result == null ? null : result.aiCallId()).build();
    }

    /**
     * 转换跨服务评审摘要。
     * @param task 评审任务
     * @param result 可空评审结果
     * @return 评审摘要
     */
    private ReviewSummaryDTO toSummary(ReviewTask task, StoredResult result) {
        return new ReviewSummaryDTO(
                task.getId(),
                task.getSubmissionId(),
                task.getTeamId(),
                task.getProblemId(),
                task.getStatus(),
                task.getWorkflowVersion(),
                result == null ? null : result.score(),
                result == null ? null : result.resultJson(),
                result == null ? null : result.modelName(),
                result == null ? null : result.aiCallId(),
                task.getErrorMessage(),
                task.getFinishedAt()
        );
    }

    private StoredResult storedResult(ReviewTask task) {
        if (EvidenceReviewV2Workflow.VERSION_CODE.equals(task.getWorkflowVersion())) {
            if (v2ResultMapper == null) return null;
            ReviewV2Result result = v2ResultMapper.selectOne(new LambdaQueryWrapper<ReviewV2Result>()
                    .eq(ReviewV2Result::getTaskId, task.getId()).last("LIMIT 1"));
            return result == null ? null : new StoredResult(result.getScore(), result.getResultJson(),
                    result.getModelName(), result.getAiCallId());
        }
        ReviewV1Result result = resultMapper.selectOne(new LambdaQueryWrapper<ReviewV1Result>()
                .eq(ReviewV1Result::getTaskId, task.getId()).last("LIMIT 1"));
        return result == null ? null : new StoredResult(result.getScore(), result.getResultJson(),
                result.getModelName(), result.getAiCallId());
    }

    private record StoredResult(java.math.BigDecimal score, String resultJson,
                                String modelName, String aiCallId) {}
    private String truncate(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.substring(0, Math.min(message.length(), 500));
    }

    /**
     * 创建正式评审 attempt 的稳定 AI 幂等键。
     *
     * @param submissionId 提交标识
     * @param workflowVersion 工作流版本
     * @param attemptNo 业务 attempt
     * @return 稳定幂等键
     */
    public static String aiIdempotencyKey(Long submissionId, String workflowVersion, int attemptNo) {
        return "review:" + submissionId + ":" + workflowVersion + ":attempt:" + attemptNo;
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }

    private String classifyExperimentFailure(Exception exception) {
        if (exception instanceof AiClientException clientException) {
            if (clientException.getCode() == 51212) return "PENDING";
            if (clientException.getCode() == 51213) return "UNKNOWN";
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("未知评审版本")) return "CONFIGURATION";
        if (message.contains("模型输出不符合") || message.contains("AI 网关未返回评审内容")) {
            return "OUTPUT";
        }
        return "ENVIRONMENT";
    }

    private String experimentErrorMessage(String failureType) {
        return switch (failureType) {
            case "CONFIGURATION" -> "评审版本不存在或不可执行";
            case "OUTPUT" -> "评审版本未产生符合契约的结果";
            case "PENDING" -> "AI 调用仍在处理中";
            case "UNKNOWN" -> "AI 上游结果未知，禁止自动重试";
            default -> "实验评审依赖暂不可用";
        };
    }
}
