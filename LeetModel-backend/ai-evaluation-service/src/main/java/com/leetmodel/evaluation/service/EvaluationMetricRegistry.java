package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.model.EvaluationMetricDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 指标口径唯一注册表；未注册编码禁止进入聚合或比较。 */
@Component
public class EvaluationMetricRegistry {

    public static final String REGISTRY_VERSION = "METRIC_SET_V1";
    private static final Set<String> ALL = Set.of("REVIEW", "ASSISTANT");
    private static final Map<String, EvaluationMetricDefinition> DEFINITIONS = definitions();

    public EvaluationMetricDefinition require(String metricCode) {
        EvaluationMetricDefinition definition = DEFINITIONS.get(metricCode);
        if (definition == null) throw new IllegalArgumentException("未知评价指标: " + metricCode);
        return definition;
    }

    public List<EvaluationMetricDefinition> listForFeature(String featureCode) {
        return DEFINITIONS.values().stream()
                .filter(definition -> definition.applicableFeatures().contains(featureCode))
                .toList();
    }

    public void requireApplicable(String metricCode, String featureCode) {
        EvaluationMetricDefinition definition = require(metricCode);
        if (!definition.applicableFeatures().contains(featureCode)) {
            throw new IllegalArgumentException(metricCode + " 不适用于 " + featureCode);
        }
    }

    private static Map<String, EvaluationMetricDefinition> definitions() {
        Map<String, EvaluationMetricDefinition> values = new LinkedHashMap<>();
        add(values, metric("RUN_SUCCESS_RATE", "RUNNING", "PERCENT", "HIGHER_IS_BETTER",
                "EVALUATION_ATTEMPT", ALL, "REQUIRE", "运行终态"));
        add(values, metric("INPUT_TOKENS", "RESOURCE", "TOKEN", "LOWER_IS_BETTER",
                "AI_GATEWAY_CALL", ALL, "MARK_UNAVAILABLE", "网关 usage"));
        add(values, metric("OUTPUT_TOKENS", "RESOURCE", "TOKEN", "LOWER_IS_BETTER",
                "AI_GATEWAY_CALL", ALL, "MARK_UNAVAILABLE", "网关 usage"));
        add(values, metric("ACTUAL_COST", "RESOURCE", "CURRENCY", "LOWER_IS_BETTER",
                "AI_GATEWAY_CALL", ALL, "MARK_UNAVAILABLE", "网关实际费用或明确估算标记"));
        add(values, metric("QUEUE_DURATION_MS", "RUNNING", "MILLISECOND", "LOWER_IS_BETTER",
                "AI_GATEWAY_CALL", ALL, "MARK_UNAVAILABLE", "网关排队时间"));
        add(values, metric("EXECUTION_DURATION_MS", "RUNNING", "MILLISECOND", "LOWER_IS_BETTER",
                "AI_GATEWAY_CALL", ALL, "MARK_UNAVAILABLE", "网关执行时间"));
        add(values, metric("TOTAL_DURATION_MS", "RUNNING", "MILLISECOND", "LOWER_IS_BETTER",
                "AI_GATEWAY_CALL", ALL, "REQUIRE", "实验调用总耗时"));
        add(values, metric("REVIEW_SCORE", "BUSINESS_OUTPUT", "SCORE", "NONE",
                "REVIEW_EXPERIMENT", Set.of("REVIEW"), "MARK_UNAVAILABLE", "有效评审结构分数"));
        add(values, metric("REVIEW_SCORE_MEAN", "BUSINESS_OUTPUT", "SCORE", "NONE",
                "REVIEW_EXPERIMENT", Set.of("REVIEW"), "MARK_UNAVAILABLE", "有效评审结构分数"));
        add(values, metric("REVIEW_SCORE_VARIANCE", "STABILITY", "SCORE_SQUARED", "LOWER_IS_BETTER",
                "EVALUATION_CALCULATION", Set.of("REVIEW"), "MARK_UNAVAILABLE", "至少两个有效重复"));
        add(values, metric("REVIEW_SCORE_STDDEV", "STABILITY", "SCORE", "LOWER_IS_BETTER",
                "EVALUATION_CALCULATION", Set.of("REVIEW"), "MARK_UNAVAILABLE", "至少两个有效重复"));
        add(values, metric("REVIEW_SCORE_RANGE", "STABILITY", "SCORE", "LOWER_IS_BETTER",
                "EVALUATION_CALCULATION", Set.of("REVIEW"), "MARK_UNAVAILABLE", "至少两个有效重复"));
        add(values, metric("STRUCTURE_VALID_RATE", "DETERMINISTIC_QUALITY", "PERCENT",
                "HIGHER_IS_BETTER", "FEATURE_RESULT_SCHEMA", ALL, "REQUIRE", "确定性 schema 校验"));
        add(values, metric("RETRIEVAL_HIT_RATE", "DETERMINISTIC_QUALITY", "PERCENT",
                "HIGHER_IS_BETTER", "ASSISTANT_RAG_TRACE", Set.of("ASSISTANT"),
                "MARK_UNAVAILABLE", "RAG 版本检索轨迹"));
        add(values, metric("SOURCE_COVERAGE_RATE", "DETERMINISTIC_QUALITY", "PERCENT",
                "HIGHER_IS_BETTER", "ASSISTANT_EXPECTED_SOURCE", Set.of("ASSISTANT"),
                "MARK_NOT_EVALUATED", "样本标准来源"));
        add(values, metric("FORMAT_RULE_PASS_RATE", "DETERMINISTIC_QUALITY", "PERCENT",
                "HIGHER_IS_BETTER", "DETERMINISTIC_RULE", Set.of("ASSISTANT"),
                "MARK_NOT_EVALUATED", "版本化格式规则"));
        add(values, metric("EXPECTED_POINT_COVERAGE_RATE", "EVIDENCE_QUALITY", "PERCENT",
                "HIGHER_IS_BETTER", "ASSISTANT_EXPECTED_POINT", Set.of("ASSISTANT"),
                "MARK_NOT_EVALUATED", "样本标准要点的确定性文本覆盖"));
        add(values, metric("HUMAN_QUALITY_SCORE", "HUMAN_QUALITY", "SCORE",
                "HIGHER_IS_BETTER", "HUMAN_ANNOTATION", ALL, "MARK_NOT_EVALUATED",
                "版本化量表与人工标注"));
        return Map.copyOf(values);
    }

    private static EvaluationMetricDefinition metric(String code, String category, String unit,
                                                       String direction, String source,
                                                       Set<String> features, String missingPolicy,
                                                       String evidence) {
        return new EvaluationMetricDefinition(code, code + "_V1", category, unit, direction,
                source, Set.copyOf(features), missingPolicy, evidence);
    }

    private static void add(Map<String, EvaluationMetricDefinition> values,
                            EvaluationMetricDefinition definition) {
        if (values.put(definition.metricCode(), definition) != null) {
            throw new IllegalStateException("重复评价指标: " + definition.metricCode());
        }
    }
}
