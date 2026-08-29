package com.leetmodel.aigateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.model.ModelExecutionSnapshot;
import com.leetmodel.aigateway.scheduling.AiPriorityPolicy;
import com.leetmodel.aigateway.scheduling.AiQueueAdmissionService;
import com.leetmodel.aigateway.scheduling.AiTaskWaitRegistry;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** 将同步内部契约映射为持久化排队和异步有界等待。 */
@Service
public class AiScheduledCallService {
    private static final Duration MAX_RESULT_WAIT = Duration.ofSeconds(120);
    private final ObjectMapper objectMapper;
    private final AiPriorityPolicy priorityPolicy;
    private final AiQueueAdmissionService admissionService;
    private final AiCallTaskMapper taskMapper;
    private final AiTaskWaitRegistry waits;
    private final ModelExecutionConfigService executionConfigs;

    public AiScheduledCallService(ObjectMapper objectMapper, AiPriorityPolicy priorityPolicy,
                                  AiQueueAdmissionService admissionService, AiCallTaskMapper taskMapper,
                                  AiTaskWaitRegistry waits, ModelExecutionConfigService executionConfigs) {
        this.objectMapper = objectMapper;
        this.priorityPolicy = priorityPolicy;
        this.admissionService = admissionService;
        this.taskMapper = taskMapper;
        this.waits = waits;
        this.executionConfigs = executionConfigs;
    }

    public CompletableFuture<AiChatResponse> chat(AiChatRequest request) {
        return submit("CHAT", request.context(), request, AiChatResponse.class);
    }

    public CompletableFuture<AiEmbeddingResponse> embed(AiEmbeddingRequest request) {
        return submit("EMBEDDING", request.context(), request, AiEmbeddingResponse.class);
    }

    private <T> CompletableFuture<T> submit(String callType, AiCallContext context, Object request,
                                             Class<T> responseType) {
        try {
            ModelExecutionSnapshot snapshot = executionConfigs.resolve(callType, context, request);
            String payload = objectMapper.writeValueAsString(request);
            AiCallTask proposed = task(callType, context, payload,
                    objectMapper.writeValueAsString(snapshot));
            AiQueueAdmissionService.AdmissionResult admitted = admissionService.enqueue(proposed);
            if (admitted.errorCode() != null) throw new BusinessException(AiGatewayErrorCode.AI_QUEUE_FULL);
            AiCallTask task = admitted.task();
            if (terminal(task)) return completed(task, responseType);
            Duration remaining = Duration.between(Instant.now(), task.getDeadline().toInstant(ZoneOffset.UTC));
            if (remaining.isNegative() || remaining.isZero()) {
                return CompletableFuture.failedFuture(
                        new BusinessException(AiGatewayErrorCode.AI_QUEUE_EXPIRED));
            }
            Duration timeout = remaining.compareTo(MAX_RESULT_WAIT) < 0 ? remaining : MAX_RESULT_WAIT;
            CompletableFuture<AiCallTask> wait = waits.register(task.getTaskId(), timeout);
            AiCallTask latest = taskMapper.selectByTaskId(task.getTaskId());
            if (terminal(latest)) waits.complete(latest);
            return wait.thenApply(completed -> parseCompleted(completed, responseType));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 调用请求序列化失败", exception);
        }
    }

    private AiCallTask task(String callType, AiCallContext context, String payload, String configSnapshot) {
        Instant now = Instant.now();
        Instant deadline = context == null ? now.plus(MAX_RESULT_WAIT) : context.deadline();
        AiPriorityPolicy.PriorityDecision priority = priorityPolicy.resolve(context);
        AiCallTask task = new AiCallTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setCallId(UUID.randomUUID().toString());
        task.setCallerService(context == null ? "LEGACY" : context.callerService());
        task.setIdempotencyKey(context == null ? "legacy:" + task.getTaskId() : context.idempotencyKey());
        task.setCallType(callType);
        task.setFeatureCode(context == null ? "LEGACY" : context.featureCode().name());
        task.setOperationCode(context == null ? "LEGACY_CHAT" : context.operationCode().name());
        task.setDeclaredPriority(context == null ? AiCallPriority.P3.name() : context.priority().name());
        task.setEffectivePriority(priority.effectivePriority().name());
        task.setState("QUEUED");
        task.setModelExecutionConfigVersion(context == null ? null : context.modelExecutionConfigVersion());
        task.setModelExecutionConfigSnapshot(configSnapshot);
        task.setRequestHash(sha256(payload));
        task.setRequestPayload(payload);
        task.setDeadline(LocalDateTime.ofInstant(deadline, ZoneOffset.UTC));
        task.setMaxQueueWaitMs(maxQueueWait(priority.effectivePriority()).toMillis());
        task.setAttemptCount(0);
        task.setVersion(0L);
        task.setCancelRequested(false);
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        task.setQueuedAt(localNow);
        task.setCreateTime(localNow);
        task.setUpdateTime(localNow);
        task.setDeleted(0);
        return task;
    }

    private Duration maxQueueWait(AiCallPriority priority) {
        return switch (priority) {
            case P0 -> Duration.ofSeconds(10);
            case P1 -> Duration.ofSeconds(60);
            case P2 -> Duration.ofSeconds(30);
            case P3 -> Duration.ofMinutes(5);
            case P4 -> Duration.ofMinutes(10);
        };
    }

    private <T> CompletableFuture<T> completed(AiCallTask task, Class<T> responseType) {
        try {
            return CompletableFuture.completedFuture(parseCompleted(task, responseType));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private <T> T parseCompleted(AiCallTask task, Class<T> responseType) {
        if ("EXPIRED".equals(task.getState())) throw new BusinessException(AiGatewayErrorCode.AI_QUEUE_EXPIRED);
        if (!"SUCCEEDED".equals(task.getState()) || task.getResultPayload() == null) {
            if ("AI_UPSTREAM_RESULT_UNKNOWN".equals(task.getErrorCode())) {
                throw new BusinessException(AiGatewayErrorCode.AI_UPSTREAM_RESULT_UNKNOWN);
            }
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
        try {
            return objectMapper.readValue(task.getResultPayload(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 调用结果反序列化失败", exception);
        }
    }

    private boolean terminal(AiCallTask task) {
        return task != null && switch (task.getState()) {
            case "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED" -> true;
            default -> false;
        };
    }

    private String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
