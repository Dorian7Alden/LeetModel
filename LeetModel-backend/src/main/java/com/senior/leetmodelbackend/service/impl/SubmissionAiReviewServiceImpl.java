package com.senior.leetmodelbackend.service.impl;

import com.senior.leetmodelbackend.entity.pojo.Problem;
import com.senior.leetmodelbackend.entity.pojo.SubmissionAiReview;
import com.senior.leetmodelbackend.mapper.ProblemMapper;
import com.senior.leetmodelbackend.mapper.SubmissionAiReviewMapper;
import com.senior.leetmodelbackend.service.SubmissionAiReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SubmissionAiReviewServiceImpl implements SubmissionAiReviewService {

    @Autowired
    private SubmissionAiReviewMapper submissionAiReviewMapper;

    @Autowired
    private ProblemMapper problemMapper;

    @Autowired
    private AiReviewTaskRunner aiReviewTaskRunner;

    @Override
    public Integer submitReview(Integer userId, Integer problemId, String submissionContent) {
        // 1. 获取题目信息，用于拼接提示词
        Problem problem = problemMapper.selectById(problemId);
        String problemTitle = problem != null ? problem.getTitle() : "未知题目";
        String problemContent = problem != null ? problem.getContent() : "暂无题目详情";

        // 2. 创建任务并持久化到数据库
        SubmissionAiReview review = new SubmissionAiReview();
        review.setUserId(userId);
        review.setProblemId(problemId);
        review.setSubmissionContent(submissionContent);
        review.setStatus("WAITING");
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());

        submissionAiReviewMapper.insert(review);
        Integer reviewId = review.getId();

        // 3. 异步执行 AI 审核
        aiReviewTaskRunner.doAiReviewAsync(reviewId, problemTitle, problemContent, submissionContent);

        return reviewId;
    }

    @Override
    public SubmissionAiReview getReviewResult(Integer id) {
        return submissionAiReviewMapper.selectById(id);
    }
}
