package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.entity.enums.error.GlobalErrorCode;
import com.senior.leetmodelbackend.entity.enums.error.ProblemErrorCode;
import com.senior.leetmodelbackend.entity.enums.error.ThirdPartyErrorCode;
import com.senior.leetmodelbackend.entity.pojo.Result;
import com.senior.leetmodelbackend.entity.pojo.SubmissionAiReview;
import com.senior.leetmodelbackend.service.SubmissionAiReviewService;
import com.senior.leetmodelbackend.utils.ArkAiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 对话和审核 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    @Autowired
    private ArkAiUtils arkAiUtils;

    @Autowired
    private SubmissionAiReviewService submissionAiReviewService;

    /**
     * 提交代码进行 AI 审核 (异步) - JSON 格式
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @param requestBody 包含 submissionContent 的请求体
     * @return 审核任务 ID
     */
    @PostMapping(value = "/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Integer> submitReview(@RequestParam Integer userId,
                                       @RequestParam Integer problemId,
                                       @RequestBody Map<String, String> requestBody) {
        try {
            String submissionContent = requestBody.get("submissionContent");
            if (submissionContent == null || submissionContent.isEmpty()) {
                return Result.error(com.senior.leetmodelbackend.entity.enums.error.ProblemErrorCode.SUBMISSION_CONTENT_BLANK);
            }
            Integer reviewId = submissionAiReviewService.submitReview(userId, problemId, submissionContent);
            return Result.success("审核任务已创建", reviewId);
        } catch (Exception e) {
            log.error("提交 AI 审核异常：", e);
            return Result.error(ThirdPartyErrorCode.AI_MODEL_CALL_FAILED, "提交审核失败：" + e.getMessage());
        }
    }

    /**
     * 提交代码进行 AI 审核 (异步) - 原始文本格式
     * 直接发送原始文本内容，无需 JSON 转义（支持 LaTeX 等特殊字符）
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @param submissionContent 代码或方案的原始文本内容
     * @return 审核任务 ID
     */
    @PostMapping(value = "/review", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Result<Integer> submitReviewRaw(@RequestParam Integer userId,
                                          @RequestParam Integer problemId,
                                          @RequestBody String submissionContent) {
        try {
            if (submissionContent == null || submissionContent.trim().isEmpty()) {
                return Result.error(ProblemErrorCode.SUBMISSION_CONTENT_BLANK);
            }
            Integer reviewId = submissionAiReviewService.submitReview(userId, problemId, submissionContent);
            return Result.success("审核任务已创建", reviewId);
        } catch (Exception e) {
            log.error("提交 AI 审核异常 (Raw)：", e);
            return Result.error(ThirdPartyErrorCode.AI_MODEL_CALL_FAILED, "提交审核失败：" + e.getMessage());
        }
    }

    /**
     * 获取 AI 审核结果
     *
     * @param id 任务 ID
     * @return 审核结果详情
     */
    @GetMapping("/review/result")
    public Result<SubmissionAiReview> getReviewResult(@RequestParam Integer id) {
        try {
            SubmissionAiReview review = submissionAiReviewService.getReviewResult(id);
            if (review == null) {
                return Result.error(GlobalErrorCode.RESOURCE_NOT_FOUND, "未找到该审核任务");
            }
            return Result.success("success", review);
        } catch (Exception e) {
            log.error("获取 AI 审核结果异常：", e);
            return Result.error(GlobalErrorCode.SYSTEM_INTERNAL_ERROR, "获取结果失败：" + e.getMessage());
        }
    }

    /**
     * 测试对话接口
     * 访问示例：/api/v1/ai/chat?prompt=你好
     *
     * @param prompt 用户输入的提示词
     * @param systemPrompt 系统提示词，用于设定 AI 角色（可选，默认为"你是人工智能助手。"）
     * @return AI 的回答
     */
    @GetMapping("/chat")
    public Result<String> chat(@RequestParam String prompt, 
                              @RequestParam(required = false, defaultValue = "你是人工智能助手。") String systemPrompt) {
        try {
            String response = arkAiUtils.chat(prompt, systemPrompt);
            return Result.success("success", response);
        } catch (Exception e) {
            log.error("AI 对话接口异常：", e);
            return Result.error(ThirdPartyErrorCode.AI_MODEL_CALL_FAILED, "AI 对话请求失败：" + e.getMessage());
        }
    }

    /**
     * 流式对话接口 (SSE - Server-Sent Events)
     * 访问示例：/api/v1/ai/stream?prompt=你好
     *
     * @param prompt 用户输入的提示词
     * @param systemPrompt 系统提示词，用于设定 AI 角色（可选，默认为"你是人工智能助手。"）
     * @return SSE 流式响应
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String prompt,
                                 @RequestParam(required = false, defaultValue = "你是人工智能助手。") String systemPrompt) {
        // 创建 SSE Emitter，超时时间设置为 0（永不过期）
        SseEmitter emitter = new SseEmitter(0L);
        
        // 订阅 Flux 流
        arkAiUtils.streamChat(prompt, systemPrompt)
            .subscribe(
                jsonContent -> {
                    try {
                        // 直接发送 JSON 格式的字符串
                        // Spring 会自动包装成 SSE 格式：data: {"id":"...","choices":[...]}
                        emitter.send(jsonContent, MediaType.APPLICATION_JSON);
                    } catch (Exception e) {
                        log.error("SSE 发送数据失败：", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("SSE 流式响应发生错误：", error);
                    emitter.completeWithError(error);
                },
                () -> {
                    log.info("SSE 流式响应完成");
                    emitter.complete();
                }
            );
        
        // 处理客户端断开连接的情况
        emitter.onCompletion(() -> {
            log.info("SSE 连接已关闭");
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            emitter.complete();
        });
        
        emitter.onError(throwable -> {
            log.error("SSE 连接发生错误：", throwable);
        });
        
        return emitter;
    }
}
