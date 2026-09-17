package com.leetmodel.aigateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 按 evaluationTaskId 聚合网关调用事实，正文始终留在网关边界内。 */
@Service
@RequiredArgsConstructor
public class AiEvaluationCallAggregationService {
    private final AiCallLogMapper mapper;

    public AiEvaluationCallAggregateDTO aggregate(String evaluationTaskId) {
        List<AiCallLog> calls = mapper.selectList(new LambdaQueryWrapper<AiCallLog>()
                .eq(AiCallLog::getEvaluationTaskId, evaluationTaskId)
                .orderByAsc(AiCallLog::getCreateTime));
        int usageComplete = (int) calls.stream()
                .filter(call -> "COMPLETE".equals(call.getUsageCompleteness())).count();
        int usageMissing = calls.size() - usageComplete;
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        int actualCosts = 0;
        int estimatedCosts = 0;
        int missingCosts = 0;
        for (AiCallLog call : calls) {
            if (call.getCostAmount() == null || call.getCostCurrency() == null
                    || !"COMPLETE".equals(call.getCostCompleteness())) {
                missingCosts++;
                continue;
            }
            costs.merge(call.getCostCurrency(), call.getCostAmount(), BigDecimal::add);
            if ("PROVIDER_REPORTED".equals(call.getCostSource())) actualCosts++;
            else estimatedCosts++;
        }
        int durationMissing = (int) calls.stream().filter(call -> call.getQueueMs() == null
                || call.getExecutionMs() == null || call.getTotalMs() == null).count();
        return new AiEvaluationCallAggregateDTO(calls.size(),
                (int) calls.stream().filter(call -> "SUCCEEDED".equals(call.getStatus())).count(),
                (int) calls.stream().filter(call -> !"SUCCEEDED".equals(call.getStatus())).count(),
                sum(calls, AiCallLog::getInputTokens), sum(calls, AiCallLog::getOutputTokens),
                sum(calls, AiCallLog::getReasoningTokens), sum(calls, AiCallLog::getCacheHitTokens),
                sum(calls, AiCallLog::getCacheCreationTokens), sum(calls, AiCallLog::getCacheMissTokens),
                sum(calls, AiCallLog::getTotalTokens), usageComplete, usageMissing,
                Map.copyOf(costs), actualCosts, estimatedCosts, missingCosts,
                average(calls, AiCallLog::getQueueMs), average(calls, AiCallLog::getExecutionMs),
                average(calls, AiCallLog::getTotalMs), durationMissing);
    }

    private Long sum(List<AiCallLog> calls, Function<AiCallLog, Long> value) {
        List<Long> present = calls.stream().map(value).filter(java.util.Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().reduce(0L, Math::addExact);
    }

    private Long average(List<AiCallLog> calls, Function<AiCallLog, Long> value) {
        List<Long> present = calls.stream().map(value).filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        BigDecimal total = present.stream().map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(present.size()), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
