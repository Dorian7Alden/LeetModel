package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.model.NormalizationAvailability;
import com.leetmodel.evaluation.model.NormalizationConfiguration;
import com.leetmodel.evaluation.model.NormalizationMethod;
import com.leetmodel.evaluation.model.NormalizationMissingPolicy;
import com.leetmodel.evaluation.model.NormalizationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 使用版本化固定边界把单个原始指标转换为零到一百分。 */
@Service
public class EvaluationNormalizationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RESULT_SCALE = 6;

    /**
     * 按固定配置归一化原始指标。
     * @param rawValue 原始指标；null 表示事实缺失
     * @param configuration 版本化归一化配置
     * @return 带显式可用性的归一化结果
     */
    public NormalizationResult normalize(BigDecimal rawValue,
                                         NormalizationConfiguration configuration) {
        // 先保留缺失与不可归一化语义，禁止用零值代替
        if (rawValue == null) return missing(configuration.missingPolicy());
        if (configuration.method() == NormalizationMethod.NOT_NORMALIZABLE) {
            return unavailable(NormalizationAvailability.NOT_NORMALIZABLE);
        }

        // 只使用配置中预先锁定的边界，不读取当前批次统计量
        BigDecimal normalized = switch (configuration.method()) {
            case HIGHER_IS_BETTER -> normalizeHigher(rawValue, configuration);
            case LOWER_IS_BETTER -> normalizeLower(rawValue, configuration);
            case TARGET_RANGE -> normalizeTargetRange(rawValue, configuration);
            case NOT_NORMALIZABLE -> throw new IllegalStateException("不可归一化方法已提前处理");
        };
        return new NormalizationResult(NormalizationAvailability.AVAILABLE, scale(normalized));
    }

    /**
     * 计算越高越好的线性得分。
     * @param value 原始值
     * @param configuration 固定边界配置
     * @return 已截断得分
     */
    private BigDecimal normalizeHigher(BigDecimal value, NormalizationConfiguration configuration) {
        if (value.compareTo(configuration.lowerBound()) <= 0) return ZERO;
        if (value.compareTo(configuration.upperBound()) >= 0) return HUNDRED;
        return ratio(value.subtract(configuration.lowerBound()),
                configuration.upperBound().subtract(configuration.lowerBound()));
    }

    /**
     * 计算越低越好的反向线性得分。
     * @param value 原始值
     * @param configuration 固定边界配置
     * @return 已截断得分
     */
    private BigDecimal normalizeLower(BigDecimal value, NormalizationConfiguration configuration) {
        if (value.compareTo(configuration.lowerBound()) <= 0) return HUNDRED;
        if (value.compareTo(configuration.upperBound()) >= 0) return ZERO;
        return ratio(configuration.upperBound().subtract(value),
                configuration.upperBound().subtract(configuration.lowerBound()));
    }

    /**
     * 计算目标区间得分，区间内满分、两侧向外线性衰减。
     * @param value 原始值
     * @param configuration 固定目标区间配置
     * @return 已截断得分
     */
    private BigDecimal normalizeTargetRange(BigDecimal value,
                                            NormalizationConfiguration configuration) {
        if (value.compareTo(configuration.lowerBound()) <= 0
                || value.compareTo(configuration.upperBound()) >= 0) return ZERO;
        if (value.compareTo(configuration.targetLowerBound()) >= 0
                && value.compareTo(configuration.targetUpperBound()) <= 0) return HUNDRED;
        if (value.compareTo(configuration.targetLowerBound()) < 0) {
            return ratio(value.subtract(configuration.lowerBound()),
                    configuration.targetLowerBound().subtract(configuration.lowerBound()));
        }
        return ratio(configuration.upperBound().subtract(value),
                configuration.upperBound().subtract(configuration.targetUpperBound()));
    }

    /**
     * 把比例转换为百分制。
     * @param numerator 分子
     * @param denominator 分母
     * @return 百分制数值
     */
    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(HUNDRED)
                .divide(denominator, RESULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 统一结果精度并去除无意义尾零。
     * @param value 百分制数值
     * @return 固定计算精度的数值
     */
    private BigDecimal scale(BigDecimal value) {
        return value.setScale(RESULT_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /**
     * 创建不携带数值的不可用结果。
     * @param availability 不可用原因
     * @return 不可用归一化结果
     */
    private NormalizationResult unavailable(NormalizationAvailability availability) {
        return new NormalizationResult(availability, null);
    }

    /**
     * 保留配置声明的缺失语义。
     * @param missingPolicy 版本化缺失策略
     * @return 不携带数值的缺失结果
     */
    private NormalizationResult missing(NormalizationMissingPolicy missingPolicy) {
        NormalizationAvailability availability = switch (missingPolicy) {
            case MARK_UNAVAILABLE -> NormalizationAvailability.UNAVAILABLE;
            case MARK_NOT_EVALUATED -> NormalizationAvailability.NOT_EVALUATED;
        };
        return unavailable(availability);
    }
}
