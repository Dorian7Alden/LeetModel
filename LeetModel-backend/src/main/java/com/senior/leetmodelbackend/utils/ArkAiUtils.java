package com.senior.leetmodelbackend.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senior.leetmodelbackend.config.ArkAiConfig;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChunk;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 火山引擎 AI 工具类
 */
@Slf4j
@Component
public class ArkAiUtils {

    @Autowired
    private ArkAiConfig arkAiConfig;

    private ArkService arkService;

    @Value("ark.ai.api-key")
    private String apiKey;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("未找到环境变量 ARK_API_KEY，AI 服务可能无法正常工作");
        }

        // 初始化 ArkService
        this.arkService = ArkService.builder()
                .apiKey(apiKey)
                .baseUrl(arkAiConfig.getBaseUrl())
                .build();
        log.info("Ark AI Service initialized with model: {}", arkAiConfig.getModel());
    }

    /**
     * 发起对话 (一问一答)
     *
     * @param prompt 用户的提问
     * @return AI 的回答内容
     */
    public String chat(String prompt) {
        return chat(prompt, "你是人工智能助手。");
    }

    /**
     * 发起对话 (一问一答)
     *
     * @param prompt 用户的提问
     * @param systemPrompt 系统提示词，用于设定 AI 的角色和行为
     * @return AI 的回答内容
     */
    public String chat(String prompt, String systemPrompt) {
        try {
            // 构建消息列表
            final List<ChatMessage> messages = new ArrayList<>();
            
            // 添加系统消息（可选）
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                ChatMessage systemMessage = ChatMessage.builder()
                        .role(ChatMessageRole.SYSTEM)
                        .content(systemPrompt)
                        .build();
                messages.add(systemMessage);
            }
            
            // 添加用户消息
            ChatMessage userMessage = ChatMessage.builder()
                    .role(ChatMessageRole.USER)
                    .content(prompt)
                    .build();
            messages.add(userMessage);

            // 构建请求对象
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(arkAiConfig.getModel())
                    .messages(messages)
                    .build();

            // 发起请求并获取响应
            var response = arkService.createChatCompletion(request);
            
            // 提取回复文本
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                Object contentObj = response.getChoices().get(0).getMessage().getContent();
                String content = contentObj != null ? contentObj.toString() : null;
                if (content != null && !content.isEmpty()) {
                    log.info("AI 回复成功，长度：{}", content.length());
                    return content.trim();
                }
            }
            
            log.warn("Ark AI 返回了空响应");
            throw new RuntimeException("AI 未返回有效内容");
        } catch (Exception e) {
            log.error("Ark AI 对话请求异常：", e);
            throw new RuntimeException("AI 对话请求失败：" + e.getMessage(), e);
        }
    }

    /**
     * 发起流式对话 (Stream 模式)
     *
     * @param prompt 用户的提问
     * @return Flux<String> 流式响应的字符串流
     */
    public Flux<String> streamChat(String prompt) {
        return streamChat(prompt, "你是人工智能助手。");
    }

    /**
     * 发起流式对话 (Stream 模式) - OpenAI 兼容格式
     *
     * @param prompt 用户的提问
     * @param systemPrompt 系统提示词，用于设定 AI 的角色和行为
     * @return Flux<String> OpenAI 格式的 SSE 流式响应
     */
    public Flux<String> streamChat(String prompt, String systemPrompt) {
        return Flux.create(emitter -> {
            try {
                // 构建消息列表
                final List<ChatMessage> messages = new ArrayList<>();
                
                // 添加系统消息（可选）
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    ChatMessage systemMessage = ChatMessage.builder()
                            .role(ChatMessageRole.SYSTEM)
                            .content(systemPrompt)
                            .build();
                    messages.add(systemMessage);
                }
                
                // 添加用户消息
                ChatMessage userMessage = ChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content(prompt)
                        .build();
                messages.add(userMessage);

                // 构建请求对象
                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .model(arkAiConfig.getModel())
                        .messages(messages)
                        .build();

                // 发起流式请求
                Flowable<ChatCompletionChunk> flowable = 
                    arkService.streamChatCompletion(request);
                
                // 生成唯一 ID
                String responseId = UUID.randomUUID().toString();
                long createdAt = System.currentTimeMillis() / 1000;
                int[] index = {0};  // 使用数组来保持可变性
                
                // ObjectMapper 用于序列化 JSON
                ObjectMapper mapper = new ObjectMapper();
                
                // 订阅流式响应
                flowable.subscribe(
                    chunk -> {
                        try {
                            // 处理每个数据块
                            if (chunk != null && chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                var choice = chunk.getChoices().get(0);
                                
                                // 构建 OpenAI 格式的响应块
                                OpenAIStreamResponse response = new OpenAIStreamResponse();
                                response.setId(responseId);
                                response.setObject("chat.completion.chunk");
                                response.setCreated(createdAt);
                                response.setModel(arkAiConfig.getModel());
                                
                                // 构建 choice
                                OpenAIStreamChoice streamChoice = new OpenAIStreamChoice();
                                streamChoice.setIndex(index[0]);
                                
                                // 构建 delta
                                OpenAIDelta delta = new OpenAIDelta();
                                
                                // 获取内容
                                if (choice.getMessage() != null) {
                                    Object contentObj = choice.getMessage().getContent();
                                    if (contentObj != null) {
                                        String content = contentObj.toString();
                                        if (content != null && !content.isEmpty()) {
                                            delta.setContent(content);
                                        }
                                    }
                                }
                                
                                streamChoice.setDelta(delta);
                                
                                // 检查是否完成
                                if (chunk.getChoices().get(0).getFinishReason() != null) {
                                    streamChoice.setFinishReason(chunk.getChoices().get(0).getFinishReason());
                                } else {
                                    streamChoice.setFinishReason("");
                                }
                                
                                response.setChoices(List.of(streamChoice));
                                
                                // 序列化为 JSON 并发送
                                String json = mapper.writeValueAsString(response);
                                emitter.next(json);
                                
                                index[0]++;
                            }
                        } catch (Exception e) {
                            log.error("处理流式数据块失败：", e);
                            emitter.error(e);
                        }
                    },
                    error -> {
                        // 处理错误
                        log.error("流式对话发生错误：", error);
                        emitter.error(error);
                    },
                    () -> {
                        // 流式响应完成，发送 [DONE]
                        log.info("流式对话完成");
                        emitter.next("[DONE]");
                        emitter.complete();
                    }
                );
                
            } catch (Exception e) {
                log.error("流式对话请求异常：", e);
                emitter.error(e);
            }
        });
    }

    /**
     * OpenAI 流式响应数据结构
     */
    @Data
    static class OpenAIStreamResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private List<OpenAIStreamChoice> choices;
    }

    /**
     * OpenAI 流式响应 Choice 数据结构
     */
    @Data
    static class OpenAIStreamChoice {
        private int index;
        private OpenAIDelta delta;
        private String finishReason;
    }

    /**
     * OpenAI Delta 数据结构
     */
    @Data
    static class OpenAIDelta {
        private String role;  // 可选，通常在第一个块中设置
        private String content;
    }

    @PreDestroy
    public void destroy() {
        if (arkService != null) {
            arkService.shutdownExecutor();
            log.info("Ark AI Service executor shut down.");
        }
    }
}
