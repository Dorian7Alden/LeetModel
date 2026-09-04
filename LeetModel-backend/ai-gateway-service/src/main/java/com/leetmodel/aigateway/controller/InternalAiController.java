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
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiModelCallStatsDTO;
import com.leetmodel.common.api.dto.AiCallFilterOptionsDTO;
import com.leetmodel.common.api.dto.AiProviderModelDTO;
import com.leetmodel.common.core.result.PageResult;
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

    /**
     * 向 AI 渠道发起同步文本嵌入向量化（Embedding）调用。
     *
     * @param request 包含文本列表与模型名称的嵌入请求对象，不能为 null
     * @return 异步包装的向量计算响应结果
     */
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
    public Result<List<AiProviderModelDTO>> listModels(@PathVariable AiProvider provider) {
        return Result.ok(aiModelService.listModels(provider).stream()
                .map(model -> new AiProviderModelDTO(
                        model.id(), model.provider().name(), model.ownedBy()))
                .toList());
    }

    /**
     * 条件查询最近的 AI 模型调用审计流水记录。
     *
     * @param query 查询过滤条件对象
     * @return 审计流水 DTO 列表
     */
    @Operation(summary = "查询最近 AI 调用审计")
    @GetMapping("/calls")
    public Result<List<AiCallLogDTO>> listCalls(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.list(query));
    }

    /**
     * 分页查询 AI 模型调用流水日志。
     *
     * @param query 分页与组合过滤条件对象
     * @return 分页包装的调用流水记录列表
     */
    @Operation(summary = "分页查询 AI 调用审计")
    @GetMapping("/calls/page")
    public Result<PageResult<AiCallLogDTO>> pageCalls(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.page(query));
    }

    /**
     * 聚合查询指定维度的 AI 调用开销与 Token 消耗总量。
     *
     * @param query 过滤条件对象
     * @return 包含 Token 统计与费用聚合的统计 DTO
     */
    @Operation(summary = "查询 AI 调用运行摘要")
    @GetMapping("/calls/stats")
    public Result<AiCallStatsDTO> callStats(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.stats(query));
    }

    /**
     * 按模型名称分类汇总调用次数、耗时与 Token 事实。
     *
     * @param query 过滤条件对象
     * @return 模型维度分类统计 DTO 列表
     */
    @Operation(summary = "按模型聚合 AI 调用事实")
    @GetMapping("/calls/model-stats")
    public Result<List<AiModelCallStatsDTO>> modelStats(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.modelStats(query));
    }

    /**
     * 查询 AI 调用审计日志的可选筛选项（调用类型、提供商、状态）。
     *
     * @return 筛选项元数据 DTO
     */
    @Operation(summary = "查询 AI 调用日志筛选项")
    @GetMapping("/calls/filter-options")
    public Result<AiCallFilterOptionsDTO> filterOptions() {
        return Result.ok(aiCallAuditService.filterOptions());
    }

    /**
     * 查询网关内存调度队列中积压与处理中的任务元数据。
     *
     * @param query 队列过滤参数对象
     * @return 队列任务状态 DTO 列表
     */
    @Operation(summary = "查询 AI 调用队列元数据")
    @GetMapping("/tasks")
    public Result<List<AiQueueTaskDTO>> queueTasks(@Valid AiQueueQueryDTO query) {
        return Result.ok(aiQueueOperationsService.list(query));
    }

    /**
     * 人工取消处于排队状态的指定 AI 调用调度任务。
     *
     * @param taskId 目标调度任务唯一 ID，不能为 null
     * @return 取消后的任务状态 DTO
     */
    @Operation(summary = "取消可取消的 AI 调用任务")
    @PostMapping("/tasks/{taskId}/cancel")
    public Result<AiQueueTaskDTO> cancelQueueTask(@PathVariable String taskId) {
        return Result.ok(aiQueueOperationsService.cancel(taskId));
    }

    /**
     * 按评测任务 ID 汇总其全量关联 AI 调用的 Token 消耗与计费事实。
     *
     * @param evaluationTaskId 评测任务 ID，不能为 null
     * @return 评测关联调用聚合结果 DTO
     */
    @Operation(summary = "按评价任务聚合 AI 调用资源事实")
    @GetMapping("/evaluations/{evaluationTaskId}/aggregate")
    public Result<AiEvaluationCallAggregateDTO> aggregateEvaluationCalls(
            @PathVariable String evaluationTaskId) {
        return Result.ok(evaluationCallAggregationService.aggregate(evaluationTaskId));
    }

    /**
     * 校验指定版本的模型执行配置是否可供特定工作流生效。
     *
     * @param version         模型配置版本号，不能为 null
     * @param callType        调用业务类型
     * @param workflowVersion 工作流版本
     * @param promptVersion   提示词版本
     * @return 模型执行配置可用性校验结果 DTO
     */
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
