package com.leetmodel.aigateway.controller;

import com.leetmodel.aigateway.service.AiCallAuditService;
import com.leetmodel.aigateway.service.AiEvaluationCallAggregationService;
import com.leetmodel.aigateway.service.AiModelService;
import com.leetmodel.aigateway.service.AiQueueOperationsService;
import com.leetmodel.aigateway.service.AiScheduledCallService;
import com.leetmodel.aigateway.service.ModelExecutionConfigService;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.ai.model.AiModelInfo;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.api.dto.ModelExecutionConfigAvailabilityDTO;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 业务服务调用 AI 网关的内部接口。
 */
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
@Tag(name = "AI 网关内部调用")
public class InternalAiController {

    private final AiScheduledCallService aiScheduledCallService;
    private final AiModelService aiModelService;
    private final AiCallAuditService aiCallAuditService;
    private final AiQueueOperationsService aiQueueOperationsService;
    private final AiEvaluationCallAggregationService evaluationCallAggregationService;
    private final ModelExecutionConfigService modelExecutionConfigService;

    /**
     * 发起同步 AI 对话。
     *
     * @param request 统一请求
     * @return 统一响应
     */
    @Operation(summary = "发起同步 AI 对话")
    @PostMapping("/chat")
    public CompletableFuture<Result<AiChatResponse>> chat(@Valid @RequestBody AiChatRequest request) {
        return aiScheduledCallService.chat(request).thenApply(Result::ok);
    }

    @Operation(summary = "发起同步 Embedding 调用")
    @PostMapping("/embeddings")
    public CompletableFuture<Result<AiEmbeddingResponse>> embeddings(
            @Valid @RequestBody AiEmbeddingRequest request) {
        return aiScheduledCallService.embed(request).thenApply(Result::ok);
    }

    /**
     * 查询供应商官方模型列表。
     *
     * @param provider 供应商
     * @return 模型列表
     */
    @Operation(summary = "查询供应商官方模型列表")
    @GetMapping("/models/{provider}")
    public Result<List<AiModelInfo>> listModels(@PathVariable AiProvider provider) {
        return Result.ok(aiModelService.listModels(provider));
    }

    @Operation(summary = "查询最近 AI 调用审计")
    @GetMapping("/calls")
    public Result<List<AiCallLogDTO>> listCalls(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.list(query));
    }

    @Operation(summary = "查询 AI 调用运行摘要")
    @GetMapping("/calls/stats")
    public Result<AiCallStatsDTO> callStats(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.stats(query));
    }

    @Operation(summary = "查询 AI 调用队列元数据")
    @GetMapping("/tasks")
    public Result<List<AiQueueTaskDTO>> queueTasks(@Valid AiQueueQueryDTO query) {
        return Result.ok(aiQueueOperationsService.list(query));
    }

    @Operation(summary = "取消可取消的 AI 调用任务")
    @PostMapping("/tasks/{taskId}/cancel")
    public Result<AiQueueTaskDTO> cancelQueueTask(@PathVariable String taskId) {
        return Result.ok(aiQueueOperationsService.cancel(taskId));
    }

    @Operation(summary = "按评价任务聚合 AI 调用资源事实")
    @GetMapping("/evaluations/{evaluationTaskId}/aggregate")
    public Result<AiEvaluationCallAggregateDTO> aggregateEvaluationCalls(
            @PathVariable String evaluationTaskId) {
        return Result.ok(evaluationCallAggregationService.aggregate(evaluationTaskId));
    }

    @Operation(summary = "检查模型执行配置能否用于指定工作流")
    @GetMapping("/model-execution-configs/{version}/availability")
    public Result<ModelExecutionConfigAvailabilityDTO> modelExecutionConfigAvailability(
            @PathVariable String version,
            @RequestParam String callType,
            @RequestParam String workflowVersion,
            @RequestParam String promptVersion) {
        return Result.ok(modelExecutionConfigService.availability(
                version, callType, workflowVersion, promptVersion));
    }
}
