package com.leetmodel.review.service;

import com.leetmodel.common.ai.client.AiClientException;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.review.config.ReviewWorkerProperties;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.enums.ReviewErrorCode;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import com.leetmodel.review.workflow.ReviewWorkflowRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 在租约保护下执行单个正式评审 attempt。
 */
@Slf4j
@Service
public class ReviewTaskWorker {

    private final ReviewTaskMapper taskMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewWorkflowRegistry workflowRegistry;
    private final ReviewTaskLogService logService;
    private final ReviewResultPersistenceService persistenceService;
    private final ReviewWorkerProperties properties;

    /**
     * 创建评审领域 Worker。
     *
     * @param taskMapper 评审任务数据访问
     * @param submissionFeignClient 提交快照客户端
     * @param workflowRegistry 工作流注册表
     * @param logService 任务步骤日志
     * @param persistenceService 结果事务持久化服务
     * @param properties Worker 配置
     */
    public ReviewTaskWorker(
            ReviewTaskMapper taskMapper,
            SubmissionFeignClient submissionFeignClient,
            ReviewWorkflowRegistry workflowRegistry,
            ReviewTaskLogService logService,
            ReviewResultPersistenceService persistenceService,
            ReviewWorkerProperties properties
    ) {
        this.taskMapper = taskMapper;
        this.submissionFeignClient = submissionFeignClient;
        this.workflowRegistry = workflowRegistry;
        this.logService = logService;
        this.persistenceService = persistenceService;
        this.properties = properties;
    }

    /**
     * 执行已领取任务；只有当前 lease token 可以提交结果或失败状态。
     *
     * @param taskId 任务标识
     * @param owner Worker 实例标识
     * @param leaseToken 本次领取的 fencing token
     */
    public void execute(Long taskId, String owner, String leaseToken) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        String idempotencyKey = ReviewService.aiIdempotencyKey(
                task.getSubmissionId(), task.getWorkflowVersion(), task.getAttemptNo());
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.markRunning(taskId, owner, leaseToken, idempotencyKey, now,
                now.plusSeconds(properties.getLeaseSeconds())) == 0) return;

        task.setStatus("RUNNING");
        task.setLeaseOwner(owner);
        task.setLeaseToken(leaseToken);
        task.setAiIdempotencyKey(idempotencyKey);
        task.setStartedAt(now);
        TraceIdUtil.setTraceId(task.getTraceId());
        try {
            executeWorkflow(task, leaseToken);
        } finally {
            TraceIdUtil.removeTraceId();
        }
    }

    private void executeWorkflow(ReviewTask task, String leaseToken) {
        ReviewTaskLog runLog = logService.start(task, "TASK_RUN", "执行评审任务",
                "submissionId=" + task.getSubmissionId());
        try {
            SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
            ReviewWorkflowResult workflowResult = workflowRegistry.required(
                    task.getWorkflowVersion()).execute(task, submission);
            ReviewTaskLog saveLog = logService.start(task, "SAVE_RESULT", "保存版本化评审结果",
                    "score=" + workflowResult.score());
            try {
                persistenceService.complete(task, submission, workflowResult, leaseToken);
                logService.succeed(saveLog, "taskStatus=COMPLETED", workflowResult.aiCallId());
            } catch (RuntimeException error) {
                logService.fail(saveLog, error);
                throw error;
            }
            logService.succeed(runLog, "score=" + workflowResult.score(), workflowResult.aiCallId());
        } catch (Exception exception) {
            logService.fail(runLog, exception);
            handleFailure(task, leaseToken, exception);
        }
    }

    private SubmissionReviewDTO requiredSubmission(Long submissionId) {
        Result<SubmissionReviewDTO> response = submissionFeignClient.getForReview(submissionId);
        BusinessException.throwIf(response == null || !response.isSuccess() || response.getData() == null,
                ReviewErrorCode.DEPENDENCY_UNAVAILABLE);
        return response.getData();
    }

    private void handleFailure(ReviewTask task, String leaseToken, Exception exception) {
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
                ? properties.getMaxAttempts() : task.getMaxAttempts();
        if (exception instanceof BusinessException && task.getAttemptNo() < maxAttempts) {
            int nextAttempt = task.getAttemptNo() + 1;
            taskMapper.scheduleRetry(task.getId(), leaseToken,
                    LocalDateTime.now().plusSeconds(retryDelaySeconds(task.getAttemptNo())),
                    "DEPENDENCY_TRANSIENT", error,
                    ReviewService.aiIdempotencyKey(
                            task.getSubmissionId(), task.getWorkflowVersion(), nextAttempt));
            return;
        }
        taskMapper.markTerminalFailure(task.getId(), leaseToken,
                "FAILED", "WORKFLOW_FAILED", error);
        log.error("AI 评审任务失败 taskId={}, attempt={}", task.getId(), task.getAttemptNo(), exception);
    }

    private long retryDelaySeconds(int failedAttempt) {
        return switch (failedAttempt) {
            case 1 -> 10L;
            case 2 -> 60L;
            default -> 300L;
        };
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) return "未知错误";
        return message.substring(0, Math.min(message.length(), 500));
    }
}
