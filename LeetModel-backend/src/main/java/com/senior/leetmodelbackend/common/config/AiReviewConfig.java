package com.senior.leetmodelbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 审核配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.review")
public class AiReviewConfig {
    
    /**
     * 系统提示词
     */
    private String systemPrompt = "你是一个专业的数学建模专家和论文评审员。请针对用户提交的数学建模方案进行深入审核。\n" +
            "你的任务是：\n" +
            "1. 仔细阅读提供的【题目内容】和【用户提交内容】。\n" +
            "2. 从模型建立的合理性、算法的可行性、结果的准确性以及表达的清晰度四个维度进行评价。\n" +
            "3. 给出一个综合评价报告 (feedback)。\n" +
            "4. 给出 [0,100] 的综合得分 (score)。\n\n" +
            "注意：你必须严格以 JSON 格式返回结果，不能包含任何其他解释性文字。JSON 结构必须如下：\n" +
            "{\n" +
            "  \"feedback\": \"这里是详细的评审报告，请使用 Markdown 格式增强可读性。\",\n" +
            "  \"score\": 85\n" +
            "}\n" +
            "确保返回的是合法的 JSON 字符串。";
}
