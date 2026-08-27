package com.leetmodel.aigateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        AiCallLog record = baseRecord(callId, request, routeProvider, routeModel, durationMs);
        record.setStatus("SUCCEEDED");
        record.setProvider(response.provider() == null ? record.getProvider() : response.provider().name());
        record.setModel(StringUtils.hasText(response.model()) ? response.model() : record.getModel());
        applyUsage(record, response.usage());
        safeInsert(record);
    }

    public void recordFailure(String callId, AiChatRequest request, String routeProvider,
                              String routeModel, RuntimeException exception, long durationMs) {
        AiCallLog record = baseRecord(callId, request, routeProvider, routeModel, durationMs);
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

    public List<AiCallLogDTO> list(AiCallQueryDTO query) {
        int limit = query.getLimit() == null ? 20 : query.getLimit();
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<AiCallLog>()
                .eq(StringUtils.hasText(query.getScene()), AiCallLog::getScene, normalized(query.getScene()))
                .eq(StringUtils.hasText(query.getProvider()), AiCallLog::getProvider, normalized(query.getProvider()))
                .eq(StringUtils.hasText(query.getModel()), AiCallLog::getModel, trimmed(query.getModel()))
                .eq(StringUtils.hasText(query.getStatus()), AiCallLog::getStatus, normalized(query.getStatus()))
                .orderByDesc(AiCallLog::getCreateTime)
                .last("LIMIT " + limit);
        return callLogMapper.selectList(wrapper).stream().map(this::toDto).toList();
    }

    public AiCallStatsDTO stats() {
        AiCallStatsDTO stats = callLogMapper.selectStats();
        return stats == null ? new AiCallStatsDTO(0L, 0L, 0L, 0L, 0L) : stats;
    }

    private AiCallLog baseRecord(String callId, AiChatRequest request, String routeProvider,
                                 String routeModel, long durationMs) {
        AiCallLog record = new AiCallLog();
        record.setCallId(callId);
        record.setScene(request.scene() == null ? UNKNOWN : request.scene().name());
        record.setProvider(StringUtils.hasText(routeProvider) ? routeProvider : UNKNOWN);
        record.setModel(StringUtils.hasText(routeModel) ? routeModel : UNKNOWN);
        record.setDurationMs(Math.max(0L, durationMs));
        record.setUsageComplete(false);
        return record;
    }

    private void applyUsage(AiCallLog record, AiUsage usage) {
        if (usage == null) return;
        record.setPromptTokens(usage.promptTokens());
        record.setCompletionTokens(usage.completionTokens());
        record.setReasoningTokens(usage.reasoningTokens());
        record.setTotalTokens(usage.totalTokens());
        record.setUsageComplete(usage.complete());
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
