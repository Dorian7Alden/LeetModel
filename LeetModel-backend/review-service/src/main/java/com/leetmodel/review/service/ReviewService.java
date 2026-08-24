package com.leetmodel.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.review.ai.ReviewModelClient;
import com.leetmodel.review.entity.ReviewResult;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.enums.ReviewErrorCode;
import com.leetmodel.review.mapper.ReviewResultMapper;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {
    public static final String WORKFLOW_VERSION = "BASIC_REVIEW_V1";
    private final ReviewTaskMapper taskMapper;
    private final ReviewResultMapper resultMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final StorageService storageService;
    private final ReviewModelClient modelClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long createTask(Long submissionId) {
        ReviewTask existing = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getSubmissionId, submissionId)
                .eq(ReviewTask::getWorkflowVersion, WORKFLOW_VERSION));
        if (existing != null) return existing.getId();
        ReviewTask task = new ReviewTask();
        task.setSubmissionId(submissionId); task.setStatus("WAITING");
        task.setWorkflowVersion(WORKFLOW_VERSION); task.setRetryCount(0); task.setNextRunAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task.getId();
    }

    @Scheduled(fixedDelayString = "${review.worker.delay-ms:2000}")
    public void processNext() {
        ReviewTask task = taskMapper.selectNextWaiting();
        if (task == null || taskMapper.claim(task.getId(), LocalDateTime.now()) == 0) return;
        try {
            execute(task);
        } catch (Exception exception) {
            log.error("基础评审失败 taskId={}", task.getId(), exception);
            task.setStatus("FAILED"); task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(truncate(exception.getMessage())); taskMapper.updateById(task);
        }
    }

    public ReviewVO getTask(Long taskId, Long userId) {
        ReviewTask task = requiredTask(taskId);
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        checkMember(submission.getTeamId(), userId);
        ReviewResult result = resultMapper.selectOne(new LambdaQueryWrapper<ReviewResult>()
                .eq(ReviewResult::getTaskId, taskId));
        return toVO(task, result);
    }

    public List<ReviewVO> listTeamResults(Long teamId, Long userId) {
        checkMember(teamId, userId);
        return resultMapper.selectList(new LambdaQueryWrapper<ReviewResult>()
                        .eq(ReviewResult::getTeamId, teamId).orderByDesc(ReviewResult::getCreateTime))
                .stream().map(result -> toVO(requiredTask(result.getTaskId()), result)).toList();
    }

    @Transactional
    public ReviewVO retry(Long taskId, Long userId) {
        ReviewTask task = requiredTask(taskId);
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        checkMember(submission.getTeamId(), userId);
        BusinessException.throwIf(!"FAILED".equals(task.getStatus()), ReviewErrorCode.TASK_NOT_FAILED);
        task.setStatus("WAITING"); task.setRetryCount(task.getRetryCount() + 1);
        task.setNextRunAt(LocalDateTime.now()); task.setStartedAt(null); task.setFinishedAt(null); task.setErrorMessage(null);
        taskMapper.updateById(task);
        return toVO(task, null);
    }

    private void execute(ReviewTask task) throws Exception {
        SubmissionReviewDTO submission = requiredSubmission(task.getSubmissionId());
        String text;
        try (InputStream input = storageService.download(submission.getObjectName());
             PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            text = new PDFTextStripper().getText(document);
        }
        String json = modelClient.review(text);
        JsonNode root = objectMapper.readTree(json);
        BigDecimal totalScore = root.path("totalScore").decimalValue();
        if (!root.has("totalScore") || totalScore.compareTo(BigDecimal.ZERO) < 0
                || totalScore.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("模型评审结果总分不合法");
        }
        ReviewResult result = new ReviewResult();
        result.setTaskId(task.getId()); result.setSubmissionId(submission.getId());
        result.setTeamId(submission.getTeamId()); result.setProblemId(submission.getProblemId());
        result.setWorkflowVersion(WORKFLOW_VERSION); result.setTotalScore(totalScore); result.setResultJson(json);
        resultMapper.insert(result);
        task.setStatus("COMPLETED"); task.setFinishedAt(LocalDateTime.now()); task.setErrorMessage(null);
        taskMapper.updateById(task);
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
    private ReviewVO toVO(ReviewTask task, ReviewResult result) {
        return ReviewVO.builder().taskId(task.getId()).submissionId(task.getSubmissionId()).status(task.getStatus())
                .workflowVersion(task.getWorkflowVersion()).retryCount(task.getRetryCount())
                .errorMessage(task.getErrorMessage()).finishedAt(task.getFinishedAt())
                .totalScore(result == null ? null : result.getTotalScore())
                .resultJson(result == null ? null : result.getResultJson()).build();
    }
    private String truncate(String message) {
        if (message == null) return "未知错误";
        return message.substring(0, Math.min(message.length(), 500));
    }
}
