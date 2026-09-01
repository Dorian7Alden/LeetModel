package com.leetmodel.aigateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCost;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.aigateway.provider.ProviderEmbeddingResponse;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiModelCallStatsDTO;
import com.leetmodel.common.api.dto.AiCallFilterOptionsDTO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 保存和查询不含 Prompt、回答与密钥的最小 AI 调用审计。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallAuditService {

    private static final String UNKNOWN = "UNRESOLVED";
    private final AiCallLogMapper callLogMapper;

    public void recordSuccess(String callId, AiChatRequest request, String routeProvider,
                              String routeModel, AiChatResponse response, long durationMs) {
        recordSuccess(callId, request, routeProvider, routeModel, response, durationMs, 0L);
    }

    public void recordSuccess(String callId, AiChatRequest request, String routeProvider,
                              String routeModel, AiChatResponse response, long durationMs, long queueMs) {
        AiCallLog record = baseRecord(callId, request, routeProvider, routeModel, durationMs, queueMs);
        record.setCallType("CHAT");
        record.setStatus("SUCCEEDED");
        record.setProvider(response.provider() == null ? record.getProvider() : response.provider().name());
        record.setModel(StringUtils.hasText(response.model()) ? response.model() : record.getModel());
        record.setProviderResponseId(response.providerResponseId());
        applyUsage(record, response.usage());
        applyCost(record, response.cost());
        safeInsert(record);
    }

    public void recordFailure(String callId, AiChatRequest request, String routeProvider,
                              String routeModel, RuntimeException exception, long durationMs) {
        recordFailure(callId, request, routeProvider, routeModel, exception, durationMs, 0L);
    }

    public void recordFailure(String callId, AiChatRequest request, String routeProvider,
                              String routeModel, RuntimeException exception, long durationMs, long queueMs) {
        AiCallLog record = baseRecord(callId, request, routeProvider, routeModel, durationMs, queueMs);
        record.setCallType("CHAT");
        record.setStatus("FAILED");
        if (exception instanceof BusinessException businessException) {
            record.setErrorCode(businessException.getCode());
            record.setErrorMessage(limit(businessException.getMessage()));
        } else {
            record.setErrorCode(50001);
            record.setErrorMessage("AI 服务调用失败");
        }
        safeInsert(record);
    }

    public void recordEmbeddingSuccess(String callId, AiEmbeddingRequest request, String provider,
                                       String model, ProviderEmbeddingResponse response,
                                       int dimension, long durationMs) {
        recordEmbeddingSuccess(callId, request, provider, model, response, dimension, durationMs, 0L);
    }

    public void recordEmbeddingSuccess(String callId, AiEmbeddingRequest request, String provider,
                                       String model, ProviderEmbeddingResponse response,
                                       int dimension, long durationMs, long queueMs) {
        AiCallLog record = baseRecord(callId, request.context(), provider, model, durationMs, queueMs);
        record.setCallType("EMBEDDING");
        record.setScene("EMBEDDING");
        record.setModality("EMBEDDING");
        record.setInputCount(request.inputs().size());
        record.setVectorDimension(dimension);
        record.setStatus("SUCCEEDED");
        record.setModel(StringUtils.hasText(response.model()) ? response.model() : model);
        record.setProviderResponseId(response.providerResponseId());
        applyUsage(record, response.usage());
        applyCost(record, response.cost());
        safeInsert(record);
    }

    public void recordEmbeddingFailure(String callId, AiEmbeddingRequest request, String provider,
                                       String model, RuntimeException exception, long durationMs) {
        recordEmbeddingFailure(callId, request, provider, model, exception, durationMs, 0L);
    }

    public void recordEmbeddingFailure(String callId, AiEmbeddingRequest request, String provider,
                                       String model, RuntimeException exception, long durationMs, long queueMs) {
        AiCallLog record = baseRecord(callId, request.context(), provider, model, durationMs, queueMs);
        record.setCallType("EMBEDDING");
        record.setScene("EMBEDDING");
        record.setModality("EMBEDDING");
        record.setInputCount(request.inputs().size());
        record.setStatus("FAILED");
        applyError(record, exception);
        safeInsert(record);
    }

    public List<AiCallLogDTO> list(AiCallQueryDTO query) {
        if (query == null) query = new AiCallQueryDTO();
        int limit = query.getLimit() == null ? 20 : query.getLimit();
        LambdaQueryWrapper<AiCallLog> wrapper = queryWrapper(query)
                .last("LIMIT " + limit);
        return callLogMapper.selectList(wrapper).stream().map(this::toDto).toList();
    }

    public PageResult<AiCallLogDTO> page(AiCallQueryDTO query) {
        if (query == null) query = new AiCallQueryDTO();
        int pageNumber = query.getPage() == null ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        Page<AiCallLog> result = callLogMapper.selectPage(
                new Page<>(pageNumber, pageSize), queryWrapper(query));
        return new PageResult<>(result.getTotal(), pageNumber, pageSize,
                result.getRecords().stream().map(this::toDto).toList());
    }

    public List<AiModelCallStatsDTO> modelStats(AiCallQueryDTO query) {
        return callLogMapper.selectModelStats(query == null ? new AiCallQueryDTO() : query);
    }

    public AiCallFilterOptionsDTO filterOptions() {
        return new AiCallFilterOptionsDTO(
                distinctValues("feature_code"),
                distinctValues("operation_code"),
                distinctValues("evaluation_task_id"),
                distinctValues("provider"),
                distinctValues("model"),
                distinctValues("status")
        );
    }

    private List<String> distinctValues(String column) {
        QueryWrapper<AiCallLog> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT " + column)
                .eq("deleted", 0)
                .isNotNull(column)
                .ne(column, "")
                .orderByAsc(column)
                .last("LIMIT 200");
        return callLogMapper.selectObjs(wrapper).stream().map(String::valueOf).toList();
    }

    private LambdaQueryWrapper<AiCallLog> queryWrapper(AiCallQueryDTO query) {
        return new LambdaQueryWrapper<AiCallLog>()
                .eq(StringUtils.hasText(query.getScene()), AiCallLog::getScene, normalized(query.getScene()))
                .eq(StringUtils.hasText(query.getModality()), AiCallLog::getModality, normalized(query.getModality()))
                .eq(StringUtils.hasText(query.getCallerService()), AiCallLog::getCallerService,
                        trimmed(query.getCallerService()))
                .eq(StringUtils.hasText(query.getFeatureCode()), AiCallLog::getFeatureCode,
                        normalized(query.getFeatureCode()))
                .eq(StringUtils.hasText(query.getOperationCode()), AiCallLog::getOperationCode,
                        normalized(query.getOperationCode()))
                .eq(StringUtils.hasText(query.getCallId()), AiCallLog::getCallId, trimmed(query.getCallId()))
                .eq(StringUtils.hasText(query.getTraceId()), AiCallLog::getTraceId, trimmed(query.getTraceId()))
                .eq(StringUtils.hasText(query.getBusinessTaskId()), AiCallLog::getBusinessTaskId,
                        trimmed(query.getBusinessTaskId()))
                .eq(StringUtils.hasText(query.getEvaluationTaskId()), AiCallLog::getEvaluationTaskId,
                        trimmed(query.getEvaluationTaskId()))
                .eq(StringUtils.hasText(query.getRagIndexVersion()), AiCallLog::getRagIndexVersion,
                        trimmed(query.getRagIndexVersion()))
                .eq(StringUtils.hasText(query.getWorkflowVersion()), AiCallLog::getWorkflowVersion,
                        trimmed(query.getWorkflowVersion()))
                .eq(StringUtils.hasText(query.getPromptVersion()), AiCallLog::getPromptVersion,
                        trimmed(query.getPromptVersion()))
                .eq(StringUtils.hasText(query.getModelExecutionConfigVersion()),
                        AiCallLog::getModelExecutionConfigVersion,
                        trimmed(query.getModelExecutionConfigVersion()))
                .eq(StringUtils.hasText(query.getProvider()), AiCallLog::getProvider, normalized(query.getProvider()))
                .eq(StringUtils.hasText(query.getModel()), AiCallLog::getModel, trimmed(query.getModel()))
                .eq(StringUtils.hasText(query.getStatus()), AiCallLog::getStatus, normalized(query.getStatus()))
                .eq(StringUtils.hasText(query.getCostSource()), AiCallLog::getCostSource,
                        normalized(query.getCostSource()))
                .ge(query.getCreatedFrom() != null, AiCallLog::getCreateTime, query.getCreatedFrom())
                .le(query.getCreatedTo() != null, AiCallLog::getCreateTime, query.getCreatedTo())
                .orderByDesc(AiCallLog::getCreateTime);
    }

    public AiCallStatsDTO stats(AiCallQueryDTO query) {
        if (query == null) query = new AiCallQueryDTO();
        AiCallStatsDTO stats = callLogMapper.selectStats(query);
        return stats == null ? new AiCallStatsDTO(0L, 0L, 0L, 0L, 0L) : stats;
    }

    public AiCallStatsDTO stats() {
        return stats(new AiCallQueryDTO());
    }

    private AiCallLog baseRecord(String callId, AiChatRequest request, String routeProvider,
                                 String routeModel, long durationMs) {
        return baseRecord(callId, request, routeProvider, routeModel, durationMs, 0L);
    }

    private AiCallLog baseRecord(String callId, AiChatRequest request, String routeProvider,
                                 String routeModel, long durationMs, long queueMs) {
        AiCallLog record = new AiCallLog();
        record.setCallId(callId);
        record.setTraceId(currentTraceId());
        String modality = request.effectiveModality() == null ? UNKNOWN : request.effectiveModality().name();
        record.setScene(modality);
        record.setModality(modality);
        applyContext(record, request.context(), callId);
        record.setProvider(StringUtils.hasText(routeProvider) ? routeProvider : UNKNOWN);
        record.setModel(StringUtils.hasText(routeModel) ? routeModel : UNKNOWN);
        record.setDurationMs(Math.max(0L, durationMs));
        record.setQueueMs(Math.max(0L, queueMs));
        record.setExecutionMs(Math.max(0L, durationMs));
        record.setTotalMs(Math.max(0L, queueMs) + Math.max(0L, durationMs));
        record.setUsageComplete(false);
        record.setUsageCompleteness(AiMetricCompleteness.UNKNOWN.name());
        applyCost(record, null);
        return record;
    }

    private AiCallLog baseRecord(String callId, AiCallContext context, String routeProvider,
                                 String routeModel, long durationMs) {
        return baseRecord(callId, context, routeProvider, routeModel, durationMs, 0L);
    }

    private AiCallLog baseRecord(String callId, AiCallContext context, String routeProvider,
                                 String routeModel, long durationMs, long queueMs) {
        AiCallLog record = new AiCallLog();
        record.setCallId(callId);
        record.setTraceId(currentTraceId());
        applyContext(record, context, callId);
        record.setProvider(StringUtils.hasText(routeProvider) ? routeProvider : UNKNOWN);
        record.setModel(StringUtils.hasText(routeModel) ? routeModel : UNKNOWN);
        record.setDurationMs(Math.max(0L, durationMs));
        record.setQueueMs(Math.max(0L, queueMs));
        record.setExecutionMs(Math.max(0L, durationMs));
        record.setTotalMs(Math.max(0L, queueMs) + Math.max(0L, durationMs));
        record.setUsageComplete(false);
        record.setUsageCompleteness(AiMetricCompleteness.UNKNOWN.name());
        applyCost(record, null);
        return record;
    }

    private void applyError(AiCallLog record, RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            record.setErrorCode(businessException.getCode());
            record.setErrorMessage(limit(businessException.getMessage()));
        } else {
            record.setErrorCode(50001);
            record.setErrorMessage("AI 服务调用失败");
        }
    }

    private String currentTraceId() {
        String value = TraceIdUtil.getTraceId();
        return value == null || value.isBlank() || value.length() > 100
                ? "unresolved:" + java.util.UUID.randomUUID() : value;
    }

    private void applyContext(AiCallLog record, AiCallContext context, String callId) {
        if (context == null) {
            record.setCallerService("LEGACY");
            record.setFeatureCode("LEGACY");
            record.setOperationCode("LEGACY_CHAT");
            record.setIdempotencyKey("legacy-call:" + callId);
            return;
        }
        record.setCallerService(context.callerService());
        record.setFeatureCode(context.featureCode().name());
        record.setOperationCode(context.operationCode().name());
        record.setBusinessTaskId(context.businessTaskId());
        record.setWorkflowVersion(context.workflowVersion());
        record.setPromptVersion(context.promptVersion());
        record.setModelExecutionConfigVersion(context.modelExecutionConfigVersion());
        record.setEvaluationTaskId(context.evaluationTaskId());
        record.setRagIndexVersion(context.ragIndexVersion());
        record.setPriority(context.priority().name());
        record.setIdempotencyKey(context.idempotencyKey());
        record.setDeadline(LocalDateTime.ofInstant(context.deadline(), ZoneOffset.UTC));
    }

    private void applyUsage(AiCallLog record, AiUsage usage) {
        if (usage == null) return;
        record.setInputTokens(usage.inputTokens());
        record.setOutputTokens(usage.outputTokens());
        record.setPromptTokens(usage.inputTokens());
        record.setCompletionTokens(usage.outputTokens());
        record.setReasoningTokens(usage.reasoningTokens());
        record.setCacheHitTokens(usage.cacheHitTokens());
        record.setCacheCreationTokens(usage.cacheCreationTokens());
        record.setCacheMissTokens(usage.cacheMissTokens());
        record.setTotalTokens(usage.totalTokens());
        record.setUsageComplete(usage.complete());
        record.setUsageCompleteness(usage.completeness().name());
    }

    private void applyCost(AiCallLog record, AiCost cost) {
        AiCost normalized = cost == null ? AiCost.unknown() : cost;
        record.setCostAmount(normalized.amount());
        record.setCostCurrency(normalized.currency());
        record.setCostSource(normalized.source().name());
        record.setPriceSnapshotVersion(normalized.priceSnapshotVersion());
        record.setCostCompleteness(normalized.completeness().name());
        record.setCostEnrichmentStatus(normalized.source() == com.leetmodel.common.ai.model.AiCostSource.UNKNOWN
                ? "PENDING" : "COMPLETED");
        record.setCostEnrichmentAttempts(0);
        record.setCostNextRetryAt(normalized.source() == com.leetmodel.common.ai.model.AiCostSource.UNKNOWN
                ? LocalDateTime.now(ZoneOffset.UTC) : null);
    }

    private void safeInsert(AiCallLog record) {
        try {
            callLogMapper.insert(record);
        } catch (RuntimeException exception) {
            // 供应商调用成功后不能因审计写入失败诱发上游重试和重复模型费用。
            log.error("AI 调用审计写入失败 callId={}, status={}",
                    record.getCallId(), record.getStatus(), exception);
        }
    }

    private AiCallLogDTO toDto(AiCallLog record) {
        AiCallLogDTO dto = new AiCallLogDTO();
        BeanUtils.copyProperties(record, dto);
        return dto;
    }

    private String normalized(String value) {
        String trimmed = trimmed(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private String limit(String message) {
        if (!StringUtils.hasText(message)) return "AI 服务调用失败";
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
