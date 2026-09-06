package com.leetmodel.knowledge.defense;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 防线 4: 全流程优雅降级保底提供者。
 * 遭遇模型外部超时、抖动或被白名单全数过滤时，根据分类调取基准权威文档保底，保障业务主链 100% 顺畅。
 */
@Slf4j
@Component
public class GracefulFallbackProvider {

    private static final List<String> OPTIMIZATION_FALLBACK = List.of(
            "数学建模/模型方法/优化模型与算法分类.md",
            "数学建模/模型方法/常用模型速查-优化类.md"
    );

    private static final List<String> EVALUATION_FALLBACK = List.of(
            "数学建模/模型方法/常用模型速查-评价类.md",
            "数学建模/题型方法/评价类题型特征.md"
    );

    private static final List<String> PREDICTION_FALLBACK = List.of(
            "数学建模/模型方法/常用模型速查-预测类.md",
            "数学建模/题型方法/预测类题型特征.md"
    );

    private static final List<String> MECHANISM_FALLBACK = List.of(
            "数学建模/模型方法/常用模型速查-统计与机理类.md",
            "数学建模/题型方法/机理分析类题型特征.md"
    );

    private static final List<String> DATA_MINING_FALLBACK = List.of(
            "数学建模/模型方法/机器学习与AI模型应用.md",
            "数学建模/题型方法/数据挖掘与统计类题型特征.md"
    );

    private static final List<String> GENERAL_FALLBACK = List.of(
            "数学建模/模型方法/优化模型与算法分类.md",
            "数学建模/题型方法/题型分类总览.md"
    );

    public List<String> getFallbackPaths(String category, Set<String> validPaths) {
        String normalized = category == null ? "" : category.toUpperCase(Locale.ROOT);
        List<String> candidates;

        if (normalized.contains("OPT") || normalized.contains("优化") || normalized.contains("规划")) {
            candidates = OPTIMIZATION_FALLBACK;
        } else if (normalized.contains("EVAL") || normalized.contains("评价")) {
            candidates = EVALUATION_FALLBACK;
        } else if (normalized.contains("PRED") || normalized.contains("预测") || normalized.contains("时序")) {
            candidates = PREDICTION_FALLBACK;
        } else if (normalized.contains("MECH") || normalized.contains("机理") || normalized.contains("微分")) {
            candidates = MECHANISM_FALLBACK;
        } else if (normalized.contains("DATA") || normalized.contains("数据") || normalized.contains("统计")) {
            candidates = DATA_MINING_FALLBACK;
        } else {
            candidates = GENERAL_FALLBACK;
        }

        log.info("触发优雅降级兜底: category={}, candidates={}", category, candidates);

        if (validPaths != null && !validPaths.isEmpty()) {
            List<String> matched = candidates.stream()
                    .filter(validPaths::contains)
                    .toList();
            if (!matched.isEmpty()) {
                return matched;
            }
            // 若预设路径不在当前白名单中，降级返回白名单中的前 1~2 篇
            return validPaths.stream().limit(2).toList();
        }
        return candidates;
    }
}
