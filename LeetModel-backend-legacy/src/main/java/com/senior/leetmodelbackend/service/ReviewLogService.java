package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.mapper.ReviewLogMapper;
import com.senior.leetmodelbackend.pojo.entity.ReviewLog;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ReviewLogService {

    private final ReviewLogMapper reviewLogMapper;

    public void logSuccess(Integer submissionId, Integer reviewId, String message, String detail) {
        ReviewLog logEntry = new ReviewLog();
        logEntry.setSubmissionId(submissionId);
        logEntry.setReviewId(reviewId);
        logEntry.setStatus("SUCCESS");
        logEntry.setMessage(message);
        logEntry.setDetail(detail);
        reviewLogMapper.insertReviewLog(logEntry);
    }

    public void logFailure(Integer submissionId, Integer reviewId, String message, String detail) {
        ReviewLog logEntry = new ReviewLog();
        logEntry.setSubmissionId(submissionId);
        logEntry.setReviewId(reviewId);
        logEntry.setStatus("FAIL");
        logEntry.setMessage(message);
        logEntry.setDetail(detail);
        reviewLogMapper.insertReviewLog(logEntry);
    }
}
