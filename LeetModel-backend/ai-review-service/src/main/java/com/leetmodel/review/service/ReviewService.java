package com.leetmodel.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.ReviewExperimentResultDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.entity.ReviewV1Result;
import com.leetmodel.review.entity.ReviewVersion;
import com.leetmodel.review.enums.ReviewErrorCode;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewVersionMapper;
import com.leetmodel.review.vo.ReviewVO;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowRegistry;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import com.leetmodel.review.workflow.v1.BasicReviewV1Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Slf4j @Service
public class ReviewService {
    public static final String WORKFLOW_VERSION = BasicReviewV1Workflow.VERSION_CODE;
    private final ReviewTaskMapper taskMapper;
    private final ReviewV1ResultMapper resultMapper;
    private final ReviewVersionMapper versionMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final ReviewWorkflowRegistry workflowRegistry;
    private final ReviewTaskLogService logService;
    private final ReviewResultPersistenceService persistenceService;

    public ReviewService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper,
                         ReviewVersionMapper versionMapper, SubmissionFeignClient submissionFeignClient,
                         TeamFeignClient teamFeignClient, ReviewWorkflowRegistry workflowRegistry,
                         ReviewTaskLogService logService, ReviewResultPersistenceService persistenceService) {
        this.taskMapper = taskMapper; this.resultMapper = resultMapper; this.versionMapper = versionMapper;
        this.submissionFeignClient = submissionFeignClient; this.teamFeignClient = teamFeignClient;
        this.workflowRegistry = workflowRegistry; this.logService = logService;
        this.persistenceService = persistenceService;
    }

    @Transactional
    public Long createTask(Long submissionId, Long teamId, Long problemId) {
        ReviewTask existing = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getSubmissionId, submissionId)
                .eq(ReviewTask::getWorkflowVersion, WORKFLOW_VERSION));
        if (existing != null) return existing.getId();
        ReviewWorkflow workflow = workflowRegistry.required(WORKFLOW_VERSION);
        ReviewTask task = new ReviewTask();
        task.setSubmissionId(submissionId); task.setVersionId(workflow.versionId());
        task.setTeamId(teamId); task.setProblemId(problemId); task.setStatus("WAITING");
        task.setWorkflowVersion(workflow.versionCode()); task.setPromptSnapshot(workflow.currentPrompt());
        task.setRetryCount(0); task.setAttemptNo(1); task.setNextRunAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
            return task.getId();
        } catch (DuplicateKeyException exception) {
            ReviewTask concurrent = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                    .eq(ReviewTask::getSubmissionId, submissionId)
                    .eq(ReviewTask::getWorkflowVersion, WORKFLOW_VERSION));
            if (concurrent != null) return concurrent.getId();
            throw exception;
        }
    }

    @Scheduled(fixedDelayString = "${review.worker.delay-ms:2000}")
    public void processNext() {
        ReviewTask task = taskMapper.selectNextWaiting();
        if (task == null || taskMapper.claim(task.getId(), LocalDateTime.now()) == 0) return;
        task.setStatus("RUNNING"); task.setStartedAt(LocalDateTime.now());
        ReviewTaskLog runLog = logService.start(task, "TASK_RUN", "执行评审任务", "submissionId=" + task.getSubmissionId());
        try {
            SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
            ReviewWorkflowResult workflowResult = workflowRegistry.required(task.getWorkflowVersion()).execute(task, submission);
            ReviewTaskLog saveLog = logService.start(task, "SAVE_RESULT", "保存 V1 结果", "score=" + workflowResult.score());
            try {
                persistenceService.completeV1(task, submission, workflowResult);
                logService.succeed(saveLog, "taskStatus=COMPLETED", workflowResult.aiCallId());
            } catch (RuntimeException error) {
                logService.fail(saveLog, error);
                throw error;
            }
            logService.succeed(runLog, "score=" + workflowResult.score(), workflowResult.aiCallId());
        } catch (Exception exception) {
            log.error("AI 评审失败 taskId={}", task.getId(), exception);
            logService.fail(runLog, exception);
            task.setStatus("FAILED"); task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(truncate(exception.getMessage())); taskMapper.updateById(task);
        }
    }

    public ReviewVO getTask(Long taskId, Long userId) {
        ReviewTask task = requiredTask(taskId);
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        checkMember(submission.getTeamId(), userId);
        ReviewV1Result result = resultMapper.selectOne(new LambdaQueryWrapper<ReviewV1Result>()
                .eq(ReviewV1Result::getTaskId, taskId));
        return toVO(task, result);
    }

    public List<ReviewVO> listTeamResults(Long teamId, Long userId) {
        checkMember(teamId, userId);
        return taskMapper.selectList(new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getTeamId, teamId).orderByDesc(ReviewTask::getCreateTime))
                .stream().map(task -> {
                    ReviewV1Result result = resultMapper.selectOne(new LambdaQueryWrapper<ReviewV1Result>()
                            .eq(ReviewV1Result::getTaskId, task.getId()));
                    return toVO(task, result);
                }).toList();
    }

    /**
     * 按提交 ID 查询最新评审摘要。
     * @param submissionId 提交 ID
     * @return 评审摘要
     */
    public ReviewSummaryDTO getSummaryBySubmission(Long submissionId) {
        ReviewTask task = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getSubmissionId, submissionId)
                .orderByDesc(ReviewTask::getCreateTime)
                .last("LIMIT 1"));
        BusinessException.throwIf(task == null, ReviewErrorCode.TASK_NOT_FOUND);
        ReviewV1Result result = resultMapper.selectOne(new LambdaQueryWrapper<ReviewV1Result>()
                .eq(ReviewV1Result::getTaskId, task.getId()));
        return toSummary(task, result);
    }

    /**
     * 查询已完成且已产生结果的评审摘要。
     * @param problemId 可选题目 ID
     * @return 评审摘要列表
     */
    public List<ReviewSummaryDTO> listCompletedSummaries(Long problemId) {
        // 评审结果表只在任务完成时写入
        LambdaQueryWrapper<ReviewV1Result> query = new LambdaQueryWrapper<>();
        if (problemId != null) query.eq(ReviewV1Result::getProblemId, problemId);
        query.orderByDesc(ReviewV1Result::getCreateTime);
        List<ReviewV1Result> results = resultMapper.selectList(query);

        // 使用稳定 taskId 关联任务状态，不依赖列表顺序
        return results.stream()
                .map(result -> {
                    ReviewTask task = taskMapper.selectById(result.getTaskId());
                    return task == null ? null : toSummary(task, result);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 获取评审任务数量。
     * @return 评审任务数量
     */
    public long count() {
        return taskMapper.selectCount(null);
    }

    /**
     * 执行一次不写入正式评审任务的版本实验，供质量评价服务使用。
     *
     * @param submissionId 已有提交标识
     * @param workflowVersion 待评价评审版本
     * @return 隔离实验结果或分类失败
     */
    public ReviewExperimentResultDTO runExperiment(Long submissionId, String workflowVersion) {
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
            ReviewWorkflowResult result = workflow.execute(transientTask, submission);
            return new ReviewExperimentResultDTO(
                    submissionId, submission.getProblemId(), workflow.versionCode(), "SUCCEEDED", null,
                    result.score(), result.resultJson(), result.modelName(), result.aiCallId(),
                    Duration.between(startedAt, LocalDateTime.now()).toMillis(), null);
        } catch (Exception exception) {
            log.warn("隔离评审实验失败 submissionId={}, workflowVersion={}, message={}",
                    submissionId, workflowVersion, exception.getMessage());
            String failureType = classifyExperimentFailure(exception);
            return new ReviewExperimentResultDTO(
                    submissionId, null, workflowVersion, "FAILED", failureType,
                    null, null, null, null,
                    Duration.between(startedAt, LocalDateTime.now()).toMillis(),
                    experimentErrorMessage(failureType));
        }
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
    private ReviewVO toVO(ReviewTask task, ReviewV1Result result) {
        ReviewVersion version = versionMapper.selectById(task.getVersionId());
        return ReviewVO.builder().taskId(task.getId()).submissionId(task.getSubmissionId()).status(task.getStatus())
                .workflowVersion(task.getWorkflowVersion())
                .versionName(version == null ? task.getWorkflowVersion() : version.getName())
                .versionDescription(version == null ? null : version.getDescription())
                .processSummary(version == null ? null : version.getProcessSummary())
                .retryCount(task.getRetryCount()).attemptNo(task.getAttemptNo()).errorMessage(task.getErrorMessage())
                .finishedAt(task.getFinishedAt()).score(result == null ? null : result.getScore())
                .resultJson(result == null ? null : result.getResultJson())
                .modelName(result == null ? null : result.getModelName()).build();
    }

    /**
     * 转换跨服务评审摘要。
     * @param task 评审任务
     * @param result 可空评审结果
     * @return 评审摘要
     */
    private ReviewSummaryDTO toSummary(ReviewTask task, ReviewV1Result result) {
        return new ReviewSummaryDTO(
                task.getId(),
                task.getSubmissionId(),
                task.getTeamId(),
                task.getProblemId(),
                task.getStatus(),
                task.getWorkflowVersion(),
                result == null ? null : result.getScore(),
                result == null ? null : result.getResultJson(),
                result == null ? null : result.getModelName(),
                result == null ? null : result.getAiCallId(),
                task.getErrorMessage(),
                task.getFinishedAt()
        );
    }
    private String truncate(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.substring(0, Math.min(message.length(), 500));
    }

    private String classifyExperimentFailure(Exception exception) {
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
            default -> "实验评审依赖暂不可用";
        };
    }
}
