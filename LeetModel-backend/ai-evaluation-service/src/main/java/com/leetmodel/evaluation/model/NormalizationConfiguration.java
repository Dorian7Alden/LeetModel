package com.leetmodel.evaluation.model;

import java.math.BigDecimal;
import java.util.Set;

/** 一条不可变、版本化并可追溯的指标归一化配置。 */
public record NormalizationConfiguration(
        String normalizationVersion,
        String metricCode,
        String metricVersion,
        String unit,
        NormalizationMethod method,
        NormalizationClippingPolicy clippingPolicy,
        NormalizationMissingPolicy missingPolicy,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        BigDecimal targetLowerBound,
        BigDecimal targetUpperBound,
        NormalizationBoundarySource boundarySource,
        String boundaryReference,
        Set<String> applicableFeatures
) {

    private static final String NOT_APPLICABLE_REFERENCE = "NOT_APPLICABLE";

    public NormalizationConfiguration {
        requireText(normalizationVersion, "归一化配置版本不能为空");
        requireText(metricCode, "指标编码不能为空");
        requireText(metricVersion, "指标口径版本不能为空");
        requireText(unit, "指标单位不能为空");
        if (method == null) throw new IllegalArgumentException("归一化方法不能为空");
        if (clippingPolicy == null) throw new IllegalArgumentException("截断策略不能为空");
        if (missingPolicy == null) throw new IllegalArgumentException("缺失策略不能为空");
        if (boundarySource == null) throw new IllegalArgumentException("边界来源不能为空");
        if (applicableFeatures == null || applicableFeatures.isEmpty()) {
            throw new IllegalArgumentException("适用功能不能为空");
        }
        applicableFeatures = Set.copyOf(applicableFeatures);

        if (method == NormalizationMethod.NOT_NORMALIZABLE) {
            requireNotNormalizableBounds(lowerBound, upperBound, targetLowerBound, targetUpperBound);
            if (boundarySource != NormalizationBoundarySource.NOT_APPLICABLE) {
                throw new IllegalArgumentException("不可归一化指标的边界来源必须为 NOT_APPLICABLE");
            }
            boundaryReference = NOT_APPLICABLE_REFERENCE;
        } else {
            requireText(boundaryReference, "固定边界引用不能为空");
            if (boundarySource == NormalizationBoundarySource.NOT_APPLICABLE) {
                throw new IllegalArgumentException("可归一化指标必须使用业务阈值或版本化基线");
            }
            requireOrderedBounds(lowerBound, upperBound);
            requireTargetBounds(method, lowerBound, upperBound, targetLowerBound, targetUpperBound);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private static void requireOrderedBounds(BigDecimal lowerBound, BigDecimal upperBound) {
        if (lowerBound == null || upperBound == null || lowerBound.compareTo(upperBound) >= 0) {
            throw new IllegalArgumentException("归一化下界必须小于上界");
        }
    }

    private static void requireTargetBounds(NormalizationMethod method,
                                            BigDecimal lowerBound,
                                            BigDecimal upperBound,
                                            BigDecimal targetLowerBound,
                                            BigDecimal targetUpperBound) {
        if (method != NormalizationMethod.TARGET_RANGE) {
            if (targetLowerBound != null || targetUpperBound != null) {
                throw new IllegalArgumentException("非目标区间方法不能配置目标边界");
            }
            return;
        }
        if (targetLowerBound == null || targetUpperBound == null
                || lowerBound.compareTo(targetLowerBound) >= 0
                || targetLowerBound.compareTo(targetUpperBound) > 0
                || targetUpperBound.compareTo(upperBound) >= 0) {
            throw new IllegalArgumentException("目标区间必须完整位于归一化上下界之间");
        }
    }

    private static void requireNotNormalizableBounds(BigDecimal lowerBound,
                                                       BigDecimal upperBound,
                                                       BigDecimal targetLowerBound,
                                                       BigDecimal targetUpperBound) {
        if (lowerBound != null || upperBound != null || targetLowerBound != null || targetUpperBound != null) {
            throw new IllegalArgumentException("不可归一化指标不能配置数值边界");
        }
    }
}
