package com.senior.leetmodelbackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.leetmodelbackend.config.AiReviewConfig;
import com.senior.leetmodelbackend.pojo.relation.SubmissionAiReview;
import com.senior.leetmodelbackend.mapper.SubmissionAiReviewMapper;
import com.senior.leetmodelbackend.utils.ArkAiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class AiReviewTaskRunner {

    @Autowired
    private SubmissionAiReviewMapper submissionAiReviewMapper;

    @Autowired
    private ArkAiUtils arkAiUtils;

    @Autowired
    private AiReviewConfig aiReviewConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Async
    public void doAiReviewAsync(Integer reviewId, String problemTitle, String problemContent, String submissionContent) {
        log.info("开始异步 AI 审核任务，ID: {}", reviewId);
        try {
            // 更新状态为正在审核
            SubmissionAiReview review = new SubmissionAiReview();
            review.setId(reviewId);
            review.setStatus("REVIEWING");
            review.setUpdateTime(LocalDateTime.now());
            submissionAiReviewMapper.update(review);

            // 拼接用户的提示词 (用户提交的作品内容 + 题目背景)
            String userPrompt = String.format(
                    "【题目标题】：%s\n\n【题目内容】：\n%s\n\n【用户提交内容】：\n%s",
                    problemTitle, problemContent, submissionContent
            );

            // 调用大模型
            String response = arkAiUtils.chat(userPrompt, aiReviewConfig.getSystemPrompt());
            log.info("AI 响应内容: {}", response);

            // 解析 JSON 结果
            String jsonStr = response.trim();
            // 清理 markdown 代码块
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            } else if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            JsonNode rootNode = objectMapper.readTree(jsonStr);
            String feedback = rootNode.path("feedback").asText();
            int score = rootNode.path("score").asInt();

            // 更新审核结果
            SubmissionAiReview updateReview = new SubmissionAiReview();
            updateReview.setId(reviewId);
            updateReview.setStatus("SUCCESS");
            updateReview.setAiFeedback(feedback);
            updateReview.setAiScore(score);
            updateReview.setUpdateTime(LocalDateTime.now());
            submissionAiReviewMapper.update(updateReview);

            log.info("AI 审核任务完成，ID: {}", reviewId);
        } catch (Exception e) {
            log.error("AI 审核任务异常，ID: {}", reviewId, e);
            SubmissionAiReview updateReview = new SubmissionAiReview();
            updateReview.setId(reviewId);
            updateReview.setStatus("FAILED");
            updateReview.setAiFeedback("审核失败：" + e.getMessage());
            updateReview.setUpdateTime(LocalDateTime.now());
            submissionAiReviewMapper.update(updateReview);
        }
    }
}
