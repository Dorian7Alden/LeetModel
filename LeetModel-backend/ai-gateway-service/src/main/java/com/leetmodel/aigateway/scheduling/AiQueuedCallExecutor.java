package com.leetmodel.aigateway.scheduling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.service.AiChatService;
import com.leetmodel.aigateway.service.AiEmbeddingService;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.aigateway.model.ModelExecutionSnapshot;
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
            ModelExecutionSnapshot snapshot = objectMapper.readValue(
                    task.getModelExecutionConfigSnapshot(), ModelExecutionSnapshot.class);
            Object response = switch (task.getCallType()) {
                case "CHAT" -> executeChat(task, queueMs, snapshot);
                case "EMBEDDING" -> executeEmbedding(task, queueMs, snapshot);
                default -> throw new IllegalArgumentException("未知 AI 调用类型");
            };
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 调用载荷序列化失败", exception);
        }
    }

    private Object executeChat(AiCallTask task, long queueMs, ModelExecutionSnapshot snapshot)
            throws JsonProcessingException {
        AiChatRequest request = objectMapper.readValue(task.getRequestPayload(), AiChatRequest.class);
        return snapshot.provider() == null
                ? chatService.chat(request, task.getCallId(), queueMs)
                : chatService.chat(request, task.getCallId(), queueMs, snapshot);
    }

    private Object executeEmbedding(AiCallTask task, long queueMs, ModelExecutionSnapshot snapshot)
            throws JsonProcessingException {
        AiEmbeddingRequest request = objectMapper.readValue(task.getRequestPayload(), AiEmbeddingRequest.class);
        return snapshot.provider() == null
                ? embeddingService.embed(request, task.getCallId(), queueMs)
                : embeddingService.embed(request, task.getCallId(), queueMs, snapshot);
    }
}
