package com.leetmodel.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.EvaluationScoreItemDTO;
import com.leetmodel.common.api.dto.EvaluationScoreResultDTO;
import com.leetmodel.common.api.dto.EvaluationWeightItemDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.evaluation.entity.EvaluationScoreResult;
import com.leetmodel.evaluation.entity.EvaluationScoreResultItem;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultMapper;
import com.leetmodel.evaluation.model.EvaluationRawMetricValue;
import com.leetmodel.evaluation.model.NormalizationBoundarySource;
import com.leetmodel.evaluation.model.NormalizationClippingPolicy;
import com.leetmodel.evaluation.model.NormalizationConfiguration;
import com.leetmodel.evaluation.model.NormalizationMethod;
import com.leetmodel.evaluation.model.NormalizationMissingPolicy;
import com.leetmodel.evaluation.model.NormalizationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 计算、读取并转换不可变版本选择指数结果。 */
@Service
@RequiredArgsConstructor
public class EvaluationScoreResultService {

    public static final String INITIAL_RESULT_VERSION = "SCORE_RESULT_V1";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCORE_SCALE = 6;

    private final EvaluationRawMetricExtractor rawMetricExtractor;
    private final EvaluationNormalizationService normalizationService;
    private final EvaluationScoreResultMapper resultMapper;
    private final EvaluationScoreResultItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    /**
     * 使用任务锁定的方案和原始指标生成首版指数结果。
     * @param task 评价任务
     * @param rawMetrics 原始指标对象
     * @param rawMetricsJson 原始指标不可变 JSON
     * @return 待原子持久化的结果和逐项贡献
     */
    public ScoreBundle calculateInitial(EvaluationTask task,
                                        EvaluationRawMetricsDTO rawMetrics,
                                        String rawMetricsJson) {
        // 只读取任务启动时的方案快照，不回查可能已停用的当前方案
        EvaluationWeightSchemeDTO scheme = readSchemeSnapshot(task.getWeightSchemeSnapshotJson());
        if (scheme.getItems() == null || scheme.getItems().isEmpty()
                || !task.getMetricSetVersion().equals(rawMetrics.getMetricSetVersion())) {
            throw new IllegalStateException("任务权重方案或原始指标口径快照不完整");
        }
        List<EvaluationScoreResultItem> items = new ArrayList<>();
        List<String> unavailableMetrics = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (EvaluationWeightItemDTO weightItem : scheme.getItems()) {
            EvaluationScoreResultItem item = calculateItem(task, rawMetrics, weightItem);
            items.add(item);
            if (item.getContributionValue() == null) {
                unavailableMetrics.add(item.getMetricCode());
            } else {
                total = total.add(item.getContributionValue());
            }
        }

        // 任一权重项缺失时整体不可计算，但仍保存其余逐项事实
        EvaluationScoreResult result = new EvaluationScoreResult();
        result.setTaskId(task.getId());
        result.setScoreResultVersion(INITIAL_RESULT_VERSION);
        result.setWeightSchemeId(task.getWeightSchemeId());
        result.setWeightSchemeVersion(task.getWeightSchemeVersion());
        result.setMetricSetVersion(task.getMetricSetVersion());
        result.setWeightSchemeSnapshotJson(task.getWeightSchemeSnapshotJson());
        result.setRawMetricsSnapshotJson(rawMetricsJson);
        result.setStatus(unavailableMetrics.isEmpty() ? "CALCULATED" : "UNAVAILABLE");
        result.setVersionSelectionIndex(unavailableMetrics.isEmpty() ? scale(total) : null);
        result.setUnavailableReason(unavailableMetrics.isEmpty() ? null
                : "缺少可计算指标: " + String.join(",", unavailableMetrics));
        result.setCreateTime(LocalDateTime.now());
        result.setUpdateTime(result.getCreateTime());
        return new ScoreBundle(result, List.copyOf(items));
    }

    /**
     * 查询一个任务的全部结果版本。
     * @param taskId 任务标识
     * @return 按创建时间升序排列的结果
     */
    public List<EvaluationScoreResultDTO> list(Long taskId) {
        List<EvaluationScoreResult> results = resultMapper.selectList(
                new LambdaQueryWrapper<EvaluationScoreResult>()
                        .eq(EvaluationScoreResult::getTaskId, taskId)
                        .orderByAsc(EvaluationScoreResult::getCreateTime)
                        .orderByAsc(EvaluationScoreResult::getId));
        if (results.isEmpty()) return List.of();
        List<Long> resultIds = results.stream().map(EvaluationScoreResult::getId).toList();
        List<EvaluationScoreResultItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<EvaluationScoreResultItem>()
                        .in(EvaluationScoreResultItem::getScoreResultId, resultIds)
                        .orderByAsc(EvaluationScoreResultItem::getId));
        Map<Long, List<EvaluationScoreResultItem>> itemsByResult = new LinkedHashMap<>();
        for (EvaluationScoreResultItem item : items) {
            itemsByResult.computeIfAbsent(item.getScoreResultId(), ignored -> new ArrayList<>()).add(item);
        }
        return results.stream()
                .map(result -> toDto(result, itemsByResult.getOrDefault(result.getId(), List.of())))
                .toList();
    }

    /**
     * 计算一个权重项的原值、归一化值和贡献值。
     * @param task 评价任务
     * @param rawMetrics 原始指标
     * @param weightItem 权重配置快照
     * @return 待持久化逐项结果
     */
    private EvaluationScoreResultItem calculateItem(EvaluationTask task,
                                                     EvaluationRawMetricsDTO rawMetrics,
                                                     EvaluationWeightItemDTO weightItem) {
        EvaluationRawMetricValue raw = rawMetricExtractor.extract(weightItem.getMetricCode(), rawMetrics);
        EvaluationScoreResultItem item = new EvaluationScoreResultItem();
        item.setMetricCode(weightItem.getMetricCode());
        item.setMetricVersion(weightItem.getMetricVersion());
        item.setRawAvailability(raw.availability());
        item.setRawValue(raw.value());
        item.setNormalizationVersion(weightItem.getNormalizationVersion());
        item.setWeightPercent(weightItem.getWeightPercent());
        if (!"AVAILABLE".equals(raw.availability())) {
            item.setNormalizationAvailability(raw.availability());
        } else {
            NormalizationResult normalized = normalizationService.normalize(
                    raw.value(), normalizationConfiguration(task, weightItem));
            item.setNormalizationAvailability(normalized.availability().name());
            item.setNormalizedValue(normalized.normalizedValue());
            if (normalized.normalizedValue() != null) {
                item.setContributionValue(scale(normalized.normalizedValue()
                        .multiply(weightItem.getWeightPercent())
                        .divide(HUNDRED, SCORE_SCALE, RoundingMode.HALF_UP)));
            }
        }
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(item.getCreateTime());
        return item;
    }

    /**
     * 从权重项快照恢复完整归一化配置。
     * @param task 评价任务
     * @param item 权重项
     * @return 归一化配置
     */
    private NormalizationConfiguration normalizationConfiguration(EvaluationTask task,
                                                                  EvaluationWeightItemDTO item) {
        return new NormalizationConfiguration(
                item.getNormalizationVersion(), item.getMetricCode(), item.getMetricVersion(), item.getUnit(),
                NormalizationMethod.valueOf(item.getNormalizationMethod()),
                NormalizationClippingPolicy.valueOf(item.getClippingPolicy()),
                NormalizationMissingPolicy.valueOf(item.getMissingPolicy()),
                item.getLowerBound(), item.getUpperBound(), item.getTargetLowerBound(),
                item.getTargetUpperBound(), NormalizationBoundarySource.valueOf(item.getBoundarySource()),
                item.getBoundaryReference(), Set.of(task.getFeatureCode()));
    }

    /**
     * 解析任务内不可变权重方案快照。
     * @param snapshotJson 方案 JSON
     * @return 权重方案
     */
    private EvaluationWeightSchemeDTO readSchemeSnapshot(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, EvaluationWeightSchemeDTO.class);
        } catch (Exception exception) {
            throw new IllegalStateException("任务权重方案快照无法读取", exception);
        }
    }

    /**
     * 转换评分结果响应。
     * @param result 结果实体
     * @param items 逐项实体
     * @return 跨服务响应
     */
    private EvaluationScoreResultDTO toDto(EvaluationScoreResult result,
                                           List<EvaluationScoreResultItem> items) {
        List<EvaluationScoreItemDTO> itemDtos = items.stream().map(this::toItemDto).toList();
        return new EvaluationScoreResultDTO(
                result.getId(), result.getScoreResultVersion(), result.getWeightSchemeId(),
                result.getWeightSchemeVersion(), result.getMetricSetVersion(), result.getStatus(),
                result.getVersionSelectionIndex(), result.getUnavailableReason(), result.getCalculatedBy(),
                result.getCreateTime(), itemDtos);
    }

    /**
     * 转换逐项贡献响应。
     * @param item 逐项实体
     * @return 跨服务响应
     */
    private EvaluationScoreItemDTO toItemDto(EvaluationScoreResultItem item) {
        return new EvaluationScoreItemDTO(
                item.getMetricCode(), item.getMetricVersion(), item.getRawAvailability(), item.getRawValue(),
                item.getNormalizationVersion(), item.getNormalizationAvailability(), item.getNormalizedValue(),
                item.getWeightPercent(), item.getContributionValue());
    }

    /**
     * 固定指数与贡献值精度。
     * @param value 原始计算值
     * @return 六位小数值
     */
    private BigDecimal scale(BigDecimal value) {
        return value.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /** 待与任务完成状态原子保存的评分结果。 */
    public record ScoreBundle(EvaluationScoreResult result, List<EvaluationScoreResultItem> items) {
    }
}
