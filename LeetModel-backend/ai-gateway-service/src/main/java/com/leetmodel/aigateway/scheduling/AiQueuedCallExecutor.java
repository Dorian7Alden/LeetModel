package com.leetmodel.aigateway.scheduling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.service.AiChatService;
import com.leetmodel.aigateway.service.AiEmbeddingService;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 从受控任务载荷执行一次原有 Chat 或 Embedding 调用。 */
@Component
public class AiQueuedCallExecutor implements AiQueuedTaskExecutor {
    private final ObjectMapper objectMapper;
    private final AiChatService chatService;
    private final AiEmbeddingService embeddingService;

    public AiQueuedCallExecutor(ObjectMapper objectMapper, AiChatService chatService,
                                AiEmbeddingService embeddingService) {
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.embeddingService = embeddingService;
    }

    @Override
    public String execute(AiCallTask task) {
        try {
            long queueMs = Math.max(0, Duration.between(task.getQueuedAt(),
                    LocalDateTime.now(ZoneOffset.UTC)).toMillis());
            Object response = switch (task.getCallType()) {
                case "CHAT" -> chatService.chat(objectMapper.readValue(
                        task.getRequestPayload(), AiChatRequest.class), task.getCallId(), queueMs);
                case "EMBEDDING" -> embeddingService.embed(objectMapper.readValue(
                        task.getRequestPayload(), AiEmbeddingRequest.class), task.getCallId(), queueMs);
                default -> throw new IllegalArgumentException("未知 AI 调用类型");
            };
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 调用载荷序列化失败", exception);
        }
    }
}
