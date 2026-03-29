package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.relation.SubmissionAiReview;

public interface SubmissionAiReviewService {
    
    /**
     * 提交内容进行 AI 审核
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @param submissionContent 提交内容
     * @return 审核任务 ID
     */
    Integer submitReview(Integer userId, Integer problemId, String submissionContent);

    /**
     * 根据 ID 获取审核结果
     * @param id 任务 ID
     * @return 审核结果
     */
    SubmissionAiReview getReviewResult(Integer id);
}
