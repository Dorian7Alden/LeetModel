package com.leetmodel.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.enums.SuggestionErrorCode;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.vo.SuggestionVO;
import com.leetmodel.suggestion.workflow.SuggestionV1Output;
import com.leetmodel.suggestion.workflow.SuggestionV1Workflow;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 管理论文建议任务的权限、幂等创建、异步执行、失败与恢复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionTaskMapper taskMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final ProblemFeignClient problemFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final SuggestionV1Workflow workflow;
    private final ObjectMapper objectMapper;

    /**
     * 为有权访问的最终提交幂等创建建议任务。
     *
     * @param submissionId 提交 ID
     * @param userId 当前用户 ID
     * @return 建议任务
     */
    @Transactional
    public SuggestionVO create(Long submissionId, Long userId) {
        SubmissionReviewDTO submission = requiredSubmission(submissionId);
        checkMember(submission.getTeamId(), userId);
        requireFinalSubmission(submission);
        ReviewSummaryDTO review = requiredCompletedReview(submissionId);
        validateSource(submission, review);

        SuggestionTask existing = findBySubmission(submissionId);
        if (existing != null) {
            return toVO(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        SuggestionTask task = new SuggestionTask();
        task.setSubmissionId(submissionId);
        task.setTeamId(submission.getTeamId());
        task.setProblemId(submission.getProblemId());
        task.setReviewTaskId(review.getTaskId());
        task.setWorkflowVersion(SuggestionV1Workflow.VERSION);
        task.setReviewWorkflowVersion(review.getWorkflowVersion());
        task.setStatus("WAITING");
        task.setPromptSnapshot(workflow.currentPrompt());
        task.setRetryCount(0);
        task.setNextRunAt(now);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        try {
            taskMapper.insert(task);
            return toVO(task);
        } catch (DuplicateKeyException exception) {
            SuggestionTask concurrent = findBySubmission(submissionId);
            if (concurrent != null) {
                return toVO(concurrent);
            }
            throw exception;
        }
    }

    /**
     * 查询任务并校验队伍成员权限。
     *
     * @param taskId 任务 ID
     * @param userId 当前用户 ID
     * @return 建议任务
     */
    public SuggestionVO get(Long taskId, Long userId) {
        SuggestionTask task = requiredTask(taskId);
        checkMember(task.getTeamId(), userId);
        return toVO(task);
    }

    /**
     * 查询指定提交的建议任务。
     *
     * @param submissionId 提交 ID
     * @param userId 当前用户 ID
     * @return 建议任务
     */
    public SuggestionVO getBySubmission(Long submissionId, Long userId) {
        SuggestionTask task = findBySubmission(submissionId);
        BusinessException.throwIf(task == null, SuggestionErrorCode.TASK_NOT_FOUND);
        checkMember(task.getTeamId(), userId);
        return toVO(task);
    }

    /**
     * 查询队伍的建议任务历史。
     *
     * @param teamId 队伍 ID
     * @param userId 当前用户 ID
     * @return 新任务优先的历史列表
     */
    public List<SuggestionVO> listTeam(Long teamId, Long userId) {
        checkMember(teamId, userId);
        return taskMapper.selectList(new LambdaQueryWrapper<SuggestionTask>()
                        .eq(SuggestionTask::getTeamId, teamId)
                        .orderByDesc(SuggestionTask::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    /**
     * 重试失败任务。
     *
     * @param taskId 任务 ID
     * @param userId 当前用户 ID
     * @return 重置后的任务
     */
    @Transactional
    public SuggestionVO retry(Long taskId, Long userId) {
        SuggestionTask task = requiredTask(taskId);
        checkMember(task.getTeamId(), userId);
        BusinessException.throwIf(!"FAILED".equals(task.getStatus()), SuggestionErrorCode.TASK_NOT_FAILED);
        int updated = taskMapper.resetForRetry(taskId, LocalDateTime.now());
        BusinessException.throwIf(updated == 0, SuggestionErrorCode.TASK_NOT_FAILED);
        task.setStatus("WAITING");
        task.setRetryCount(task.getRetryCount() + 1);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        task.setResultJson(null);
        task.setModelName(null);
        task.setAiCallId(null);
        return toVO(task);
    }

    /**
     * 执行一个等待中的建议任务。
     */
    @Scheduled(fixedDelayString = "${suggestion.worker.delay-ms:2000}")
    public void processNext() {
        LocalDateTime now = LocalDateTime.now();
        SuggestionTask task = taskMapper.selectNextWaiting(now);
        if (task == null || taskMapper.claim(task.getId(), now) == 0) {
            return;
        }
        task.setStatus("RUNNING");
        task.setStartedAt(now);
        try {
            SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
            ReviewSummaryDTO review = requiredCompletedReview(task.getSubmissionId());
            ProblemContextDTO problem = requiredData(
                    () -> problemFeignClient.getProblemContext(task.getProblemId()));
            validateTaskSource(task, submission, review, problem);
            SuggestionWorkflowResult result = workflow.execute(task, submission, problem, review);
            task.setStatus("COMPLETED");
            task.setResultJson(result.resultJson());
            task.setModelName(result.modelName());
            task.setAiCallId(result.aiCallId());
            task.setFinishedAt(LocalDateTime.now());
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

    /**
     * 自动恢复长时间停留在运行态的中断任务。
     */
    @Scheduled(fixedDelayString = "${suggestion.worker.recovery-delay-ms:60000}")
    public void recoverStaleTasks() {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.recoverStale(now.minusMinutes(10), now);
    }

    /**
     * 获取任务总数。
     *
     * @return 任务总数
     */
    public long count() {
        return taskMapper.selectCount(null);
    }

    /**
     * 获取最近任务摘要供管理端聚合。
     *
     * @param limit 安全限制 1 到 100
     * @return 最近任务
     */
    public List<SuggestionTaskSummaryDTO> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return taskMapper.selectList(new LambdaQueryWrapper<SuggestionTask>()
                        .orderByDesc(SuggestionTask::getCreateTime)
                        .last("LIMIT " + safeLimit))
                .stream().map(this::toSummary).toList();
    }

    private SubmissionReviewDTO requiredSubmission(Long submissionId) {
        return requiredData(() -> submissionFeignClient.getForReview(submissionId));
    }

    private void requireFinalSubmission(SubmissionReviewDTO submission) {
        List<SubmissionSnapshotDTO> finals = requiredData(
                () -> submissionFeignClient.listFinalSubmissions(submission.getProblemId()));
        boolean found = finals.stream().anyMatch(item -> item != null
                && Objects.equals(item.getId(), submission.getId())
                && Objects.equals(item.getTeamId(), submission.getTeamId())
                && Boolean.TRUE.equals(item.getFinalVersion()));
        BusinessException.throwIf(!found, SuggestionErrorCode.FINAL_SUBMISSION_REQUIRED);
    }

    private ReviewSummaryDTO requiredCompletedReview(Long submissionId) {
        Result<ReviewSummaryDTO> response;
        try {
            response = reviewFeignClient.getBySubmission(submissionId);
        } catch (RuntimeException exception) {
            throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        if (response == null || !response.isSuccess() || response.getData() == null) {
            if (response != null && response.getCode() >= 50000) {
                throw new BusinessException(SuggestionErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            throw new BusinessException(SuggestionErrorCode.REVIEW_NOT_READY);
        }
        ReviewSummaryDTO review = response.getData();
        BusinessException.throwIf(!"COMPLETED".equals(review.getStatus())
                        || review.getResultJson() == null || review.getResultJson().isBlank(),
                SuggestionErrorCode.REVIEW_NOT_READY);
        return review;
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
        BusinessException.throwIf(!Objects.equals(task.getSubmissionId(), submission.getId())
                        || !Objects.equals(task.getTeamId(), submission.getTeamId())
                        || !Objects.equals(task.getProblemId(), submission.getProblemId())
                        || !Objects.equals(task.getReviewTaskId(), review.getTaskId())
                        || !Objects.equals(task.getProblemId(), problem.getId()),
                SuggestionErrorCode.SOURCE_DATA_INVALID);
    }

    private SuggestionTask findBySubmission(Long submissionId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<SuggestionTask>()
                .eq(SuggestionTask::getSubmissionId, submissionId)
                .eq(SuggestionTask::getWorkflowVersion, SuggestionV1Workflow.VERSION)
                .last("LIMIT 1"));
    }

    private SuggestionTask requiredTask(Long taskId) {
        SuggestionTask task = taskMapper.selectById(taskId);
        BusinessException.throwIf(task == null, SuggestionErrorCode.TASK_NOT_FOUND);
        return task;
    }

    private SuggestionVO toVO(SuggestionTask task) {
        SuggestionV1Output output = null;
        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            try {
                output = objectMapper.readValue(task.getResultJson(), SuggestionV1Output.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("已保存的论文建议结果无法解析", exception);
            }
        }
        return SuggestionVO.builder()
                .taskId(task.getId())
                .submissionId(task.getSubmissionId())
                .teamId(task.getTeamId())
                .problemId(task.getProblemId())
                .reviewTaskId(task.getReviewTaskId())
                .workflowVersion(task.getWorkflowVersion())
                .reviewWorkflowVersion(task.getReviewWorkflowVersion())
                .status(task.getStatus())
                .retryCount(task.getRetryCount())
                .errorMessage(task.getErrorMessage())
                .result(output)
                .modelName(task.getModelName())
                .aiCallId(task.getAiCallId())
                .createTime(task.getCreateTime())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }

    private SuggestionTaskSummaryDTO toSummary(SuggestionTask task) {
        return new SuggestionTaskSummaryDTO(
                task.getId(), task.getSubmissionId(), task.getTeamId(), task.getProblemId(),
                task.getStatus(), task.getWorkflowVersion(), task.getModelName(), task.getAiCallId(),
                task.getErrorMessage(), task.getCreateTime(), task.getFinishedAt());
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
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message.substring(0, Math.min(message.length(), 500));
    }
}
