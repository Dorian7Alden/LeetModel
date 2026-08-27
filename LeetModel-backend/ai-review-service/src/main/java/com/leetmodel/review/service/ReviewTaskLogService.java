package com.leetmodel.review.service;

import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.mapper.ReviewTaskLogMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ReviewTaskLogService {
    private final ReviewTaskLogMapper mapper;

    public ReviewTaskLogService(ReviewTaskLogMapper mapper) {
        this.mapper = mapper;
    }

    public ReviewTaskLog start(ReviewTask task, String stepCode, String stepName, String inputSummary) {
        ReviewTaskLog log = new ReviewTaskLog();
        log.setTaskId(task.getId());
        log.setWorkflowVersion(task.getWorkflowVersion());
        log.setAttemptNo(task.getAttemptNo());
        log.setStepCode(stepCode);
        log.setStepName(stepName);
        log.setStatus("RUNNING");
        log.setStartedAt(LocalDateTime.now());
        log.setInputSummary(inputSummary);
        if (task.getId() != null) {
            mapper.insert(log);
        }
        return log;
    }

    public void succeed(ReviewTaskLog log, String outputSummary, String aiCallId) {
        finish(log, "SUCCEEDED", outputSummary, aiCallId, null);
    }

    public void fail(ReviewTaskLog log, Throwable error) {
        finish(log, "FAILED", null, null, truncate(error.getMessage()));
    }

    private void finish(ReviewTaskLog log, String status, String outputSummary, String aiCallId, String errorMessage) {
        LocalDateTime finishedAt = LocalDateTime.now();
        log.setStatus(status);
        log.setFinishedAt(finishedAt);
        log.setDurationMs(Duration.between(log.getStartedAt(), finishedAt).toMillis());
        log.setOutputSummary(outputSummary);
        log.setAiCallId(aiCallId);
        log.setErrorMessage(errorMessage);
        if (log.getId() != null) {
            mapper.updateById(log);
        }
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) return "未知错误";
        return value.substring(0, Math.min(value.length(), 500));
    }
}
