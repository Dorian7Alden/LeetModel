package com.leetmodel.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.EvaluationWeightItemCreateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightItemDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeCreateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.evaluation.entity.EvaluationWeightScheme;
import com.leetmodel.evaluation.entity.EvaluationWeightSchemeItem;
import com.leetmodel.evaluation.enums.EvaluationErrorCode;
import com.leetmodel.evaluation.model.EvaluationMetricDefinition;
import com.leetmodel.evaluation.model.NormalizationBoundarySource;
import com.leetmodel.evaluation.model.NormalizationClippingPolicy;
import com.leetmodel.evaluation.model.NormalizationConfiguration;
import com.leetmodel.evaluation.model.NormalizationMethod;
import com.leetmodel.evaluation.model.NormalizationMissingPolicy;
import com.leetmodel.evaluation.mapper.EvaluationWeightSchemeItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationWeightSchemeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 版本化权重方案的校验、保存、查询和停用服务。 */
@Service
@RequiredArgsConstructor
public class EvaluationWeightSchemeService {

    private static final BigDecimal TOTAL_WEIGHT = new BigDecimal("100.0000");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final EvaluationMetricRegistry metricRegistry;
    private final EvaluationWeightSchemePersistenceService persistenceService;
    private final EvaluationWeightSchemeMapper schemeMapper;
    private final EvaluationWeightSchemeItemMapper itemMapper;

    /**
     * 校验并创建不可变权重方案。
     * @param request 创建请求
     * @return 已保存方案
     */
    public EvaluationWeightSchemeDTO create(EvaluationWeightSchemeCreateDTO request) {
        // 校验方案口径和全部指标配置
        String featureCode = request.getFeatureCode().trim();
        BusinessException.throwIf(
                !EvaluationMetricRegistry.REGISTRY_VERSION.equals(request.getMetricSetVersion().trim()),
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID
        );
        List<EvaluationWeightSchemeItem> items = validateItems(featureCode, request.getItems());

        // 原子保存新版本；唯一版本冲突明确返回业务错误
        EvaluationWeightScheme scheme = toEntity(request, featureCode);
        try {
            persistenceService.create(scheme, items);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(EvaluationErrorCode.WEIGHT_SCHEME_VERSION_DUPLICATE);
        }
        return toDto(scheme, items);
    }

    /**
     * 按可选功能和状态查询权重方案。
     * @param featureCode 功能编码，可空
     * @param status ACTIVE/INACTIVE，可空
     * @return 最近创建的方案列表
     */
    public List<EvaluationWeightSchemeDTO> list(String featureCode, String status) {
        // 校验可选状态，避免把任意值拼进查询语义
        String normalizedFeature = trimToNull(featureCode);
        String normalizedStatus = trimToNull(status);
        BusinessException.throwIf(
                normalizedStatus != null && !STATUSES.contains(normalizedStatus),
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID
        );

        // 一次查询主表和明细，保持评价数据只从本服务读取
        LambdaQueryWrapper<EvaluationWeightScheme> query = new LambdaQueryWrapper<>();
        if (normalizedFeature != null) query.eq(EvaluationWeightScheme::getFeatureCode, normalizedFeature);
        if (normalizedStatus != null) query.eq(EvaluationWeightScheme::getStatus, normalizedStatus);
        query.orderByDesc(EvaluationWeightScheme::getCreateTime);
        List<EvaluationWeightScheme> schemes = schemeMapper.selectList(query);
        if (schemes.isEmpty()) return List.of();
        Map<Long, List<EvaluationWeightSchemeItem>> itemsByScheme = itemsByScheme(schemes);
        return schemes.stream()
                .map(scheme -> toDto(scheme, itemsByScheme.getOrDefault(scheme.getId(), List.of())))
                .toList();
    }

    /**
     * 停用权重方案但保留方案内容和全部历史引用。
     * @param schemeId 方案标识
     * @param operatorId 操作者标识
     * @return 停用后的方案
     */
    public EvaluationWeightSchemeDTO deactivate(Long schemeId, Long operatorId) {
        // 已停用请求保持幂等；主表条件更新处理并发停用
        EvaluationWeightScheme scheme = requiredScheme(schemeId);
        if ("ACTIVE".equals(scheme.getStatus())) {
            schemeMapper.deactivate(schemeId, operatorId, LocalDateTime.now());
            scheme = requiredScheme(schemeId);
        }

        // 明细从不更新或删除，旧任务仍可引用同一版本
        List<EvaluationWeightSchemeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<EvaluationWeightSchemeItem>()
                        .eq(EvaluationWeightSchemeItem::getSchemeId, schemeId)
                        .orderByAsc(EvaluationWeightSchemeItem::getId));
        return toDto(scheme, items);
    }

    /**
     * 获取可供新评价任务锁定的活动方案。
     * @param schemeId 方案标识
     * @param featureCode 任务功能
     * @param metricSetVersion 任务指标集版本
     * @return 完整方案快照
     */
    public EvaluationWeightSchemeDTO requireActiveForTask(Long schemeId,
                                                          String featureCode,
                                                          String metricSetVersion) {
        EvaluationWeightScheme scheme = requiredScheme(schemeId);
        BusinessException.throwIf(
                !"ACTIVE".equals(scheme.getStatus())
                        || !featureCode.equals(scheme.getFeatureCode())
                        || !metricSetVersion.equals(scheme.getMetricSetVersion()),
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID
        );
        List<EvaluationWeightSchemeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<EvaluationWeightSchemeItem>()
                        .eq(EvaluationWeightSchemeItem::getSchemeId, schemeId)
                        .orderByAsc(EvaluationWeightSchemeItem::getId));
        BusinessException.throwIf(items.isEmpty(), EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
        return toDto(scheme, items);
    }

    /**
     * 校验指标并转换为持久化快照。
     * @param featureCode 适用功能
     * @param requests 指标请求
     * @return 已校验指标实体
     */
    private List<EvaluationWeightSchemeItem> validateItems(
            String featureCode, List<EvaluationWeightItemCreateDTO> requests) {
        List<EvaluationWeightSchemeItem> items = new ArrayList<>();
        Set<String> metricCodes = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (EvaluationWeightItemCreateDTO request : requests) {
            String metricCode = request.getMetricCode().trim();
            BusinessException.throwIf(!metricCodes.add(metricCode), EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
            EvaluationMetricDefinition definition = requiredDefinition(metricCode, featureCode);
            NormalizationConfiguration configuration = requiredConfiguration(request, definition, featureCode);
            requireCompatible(definition, configuration);
            items.add(toItem(request, definition, configuration));
            total = total.add(request.getWeightPercent());
        }
        BusinessException.throwIf(total.compareTo(TOTAL_WEIGHT) != 0,
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
        return items;
    }

    /**
     * 从注册表读取适用指标定义。
     * @param metricCode 指标编码
     * @param featureCode 功能编码
     * @return 注册指标定义
     */
    private EvaluationMetricDefinition requiredDefinition(String metricCode, String featureCode) {
        try {
            metricRegistry.requireApplicable(metricCode, featureCode);
            return metricRegistry.require(metricCode);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
        }
    }

    /**
     * 构造并验证完整归一化配置。
     * @param request 指标请求
     * @param definition 注册指标
     * @param featureCode 功能编码
     * @return 归一化配置
     */
    private NormalizationConfiguration requiredConfiguration(EvaluationWeightItemCreateDTO request,
                                                               EvaluationMetricDefinition definition,
                                                               String featureCode) {
        BusinessException.throwIf(
                !definition.metricVersion().equals(request.getMetricVersion().trim())
                        || !definition.unit().equals(request.getUnit().trim()),
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID
        );
        try {
            return new NormalizationConfiguration(
                    request.getNormalizationVersion().trim(), definition.metricCode(),
                    definition.metricVersion(), definition.unit(),
                    NormalizationMethod.valueOf(request.getNormalizationMethod().trim()),
                    NormalizationClippingPolicy.valueOf(request.getClippingPolicy().trim()),
                    NormalizationMissingPolicy.valueOf(request.getMissingPolicy().trim()),
                    request.getLowerBound(), request.getUpperBound(),
                    request.getTargetLowerBound(), request.getTargetUpperBound(),
                    NormalizationBoundarySource.valueOf(request.getBoundarySource().trim()),
                    trimToNull(request.getBoundaryReference()), Set.of(featureCode));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
        }
    }

    /**
     * 校验注册指标方向、缺失语义和归一化方法相容。
     * @param definition 注册指标
     * @param configuration 归一化配置
     */
    private void requireCompatible(EvaluationMetricDefinition definition,
                                   NormalizationConfiguration configuration) {
        boolean directionMatches = switch (definition.direction()) {
            case "HIGHER_IS_BETTER" -> configuration.method() == NormalizationMethod.HIGHER_IS_BETTER;
            case "LOWER_IS_BETTER" -> configuration.method() == NormalizationMethod.LOWER_IS_BETTER;
            case "TARGET_RANGE" -> configuration.method() == NormalizationMethod.TARGET_RANGE;
            default -> false;
        };
        BusinessException.throwIf(!directionMatches, EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
        NormalizationMissingPolicy expectedMissing = "MARK_NOT_EVALUATED".equals(definition.missingPolicy())
                ? NormalizationMissingPolicy.MARK_NOT_EVALUATED
                : NormalizationMissingPolicy.MARK_UNAVAILABLE;
        BusinessException.throwIf(configuration.missingPolicy() != expectedMissing,
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
    }

    /**
     * 创建权重方案实体。
     * @param request 创建请求
     * @param featureCode 规范化功能编码
     * @return 主表实体
     */
    private EvaluationWeightScheme toEntity(EvaluationWeightSchemeCreateDTO request, String featureCode) {
        LocalDateTime now = LocalDateTime.now();
        EvaluationWeightScheme scheme = new EvaluationWeightScheme();
        scheme.setSchemeCode(request.getSchemeCode().trim());
        scheme.setSchemeVersion(request.getSchemeVersion().trim());
        scheme.setName(request.getName().trim());
        scheme.setObjective(request.getObjective().trim());
        scheme.setFeatureCode(featureCode);
        scheme.setMetricSetVersion(request.getMetricSetVersion().trim());
        scheme.setStatus("ACTIVE");
        scheme.setCreatedBy(request.getCreatedBy());
        scheme.setCreateTime(now);
        scheme.setUpdateTime(now);
        return scheme;
    }

    /**
     * 创建权重指标实体。
     * @param request 指标请求
     * @param definition 注册指标
     * @param configuration 归一化配置
     * @return 明细实体
     */
    private EvaluationWeightSchemeItem toItem(EvaluationWeightItemCreateDTO request,
                                               EvaluationMetricDefinition definition,
                                               NormalizationConfiguration configuration) {
        EvaluationWeightSchemeItem item = new EvaluationWeightSchemeItem();
        item.setMetricCode(definition.metricCode());
        item.setMetricVersion(definition.metricVersion());
        item.setUnit(definition.unit());
        item.setNormalizationVersion(configuration.normalizationVersion());
        item.setNormalizationMethod(configuration.method().name());
        item.setClippingPolicy(configuration.clippingPolicy().name());
        item.setMissingPolicy(configuration.missingPolicy().name());
        item.setLowerBound(configuration.lowerBound());
        item.setUpperBound(configuration.upperBound());
        item.setTargetLowerBound(configuration.targetLowerBound());
        item.setTargetUpperBound(configuration.targetUpperBound());
        item.setBoundarySource(configuration.boundarySource().name());
        item.setBoundaryReference(configuration.boundaryReference());
        item.setWeightPercent(request.getWeightPercent());
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(item.getCreateTime());
        return item;
    }

    /**
     * 按方案批量读取并分组指标明细。
     * @param schemes 权重方案列表
     * @return 方案到明细的映射
     */
    private Map<Long, List<EvaluationWeightSchemeItem>> itemsByScheme(List<EvaluationWeightScheme> schemes) {
        List<Long> ids = schemes.stream().map(EvaluationWeightScheme::getId).toList();
        List<EvaluationWeightSchemeItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<EvaluationWeightSchemeItem>()
                        .in(EvaluationWeightSchemeItem::getSchemeId, ids)
                        .orderByAsc(EvaluationWeightSchemeItem::getId));
        Map<Long, List<EvaluationWeightSchemeItem>> grouped = new LinkedHashMap<>();
        for (EvaluationWeightSchemeItem item : items) {
            grouped.computeIfAbsent(item.getSchemeId(), ignored -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /**
     * 获取存在的权重方案。
     * @param schemeId 方案标识
     * @return 权重方案实体
     */
    private EvaluationWeightScheme requiredScheme(Long schemeId) {
        EvaluationWeightScheme scheme = schemeMapper.selectById(schemeId);
        BusinessException.throwIf(scheme == null, EvaluationErrorCode.WEIGHT_SCHEME_NOT_FOUND);
        return scheme;
    }

    /**
     * 转换权重方案响应。
     * @param scheme 主表实体
     * @param items 明细实体
     * @return 跨服务响应
     */
    private EvaluationWeightSchemeDTO toDto(EvaluationWeightScheme scheme,
                                             List<EvaluationWeightSchemeItem> items) {
        List<EvaluationWeightItemDTO> itemDtos = items.stream().map(this::toItemDto).toList();
        return new EvaluationWeightSchemeDTO(
                scheme.getId(), scheme.getSchemeCode(), scheme.getSchemeVersion(), scheme.getName(),
                scheme.getObjective(), scheme.getFeatureCode(), scheme.getMetricSetVersion(),
                scheme.getStatus(), scheme.getCreatedBy(), scheme.getCreateTime(),
                scheme.getDeactivatedBy(), scheme.getDeactivatedAt(), itemDtos);
    }

    /**
     * 转换权重指标响应。
     * @param item 明细实体
     * @return 跨服务响应
     */
    private EvaluationWeightItemDTO toItemDto(EvaluationWeightSchemeItem item) {
        return new EvaluationWeightItemDTO(
                item.getMetricCode(), item.getMetricVersion(), item.getUnit(),
                item.getNormalizationVersion(), item.getNormalizationMethod(),
                item.getClippingPolicy(), item.getMissingPolicy(), item.getLowerBound(),
                item.getUpperBound(), item.getTargetLowerBound(), item.getTargetUpperBound(),
                item.getBoundarySource(), item.getBoundaryReference(), item.getWeightPercent());
    }

    /**
     * 去除可选文本首尾空白并把空串转换为 null。
     * @param value 原始文本
     * @return 规范化文本
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
