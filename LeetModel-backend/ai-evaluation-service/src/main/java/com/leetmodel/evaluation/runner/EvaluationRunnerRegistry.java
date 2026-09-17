package com.leetmodel.evaluation.runner;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 功能 Runner 注册表，评价核心不按 feature 堆叠条件分支。 */
@Component
public class EvaluationRunnerRegistry {

    private final Map<String, EvaluationExperimentRunner> runners;

    public EvaluationRunnerRegistry(List<EvaluationExperimentRunner> candidates) {
        Map<String, EvaluationExperimentRunner> values = new LinkedHashMap<>();
        for (EvaluationExperimentRunner runner : candidates) {
            String featureCode = required(runner.featureCode());
            if (values.put(featureCode, runner) != null) {
                throw new IllegalStateException("重复评价 Runner: " + featureCode);
            }
        }
        this.runners = Map.copyOf(values);
    }

    public EvaluationExperimentRunner require(String featureCode) {
        EvaluationExperimentRunner runner = runners.get(required(featureCode));
        if (runner == null) throw new IllegalArgumentException("未知或未启用的评价功能: " + featureCode);
        return runner;
    }

    public List<String> featureCodes() {
        return runners.keySet().stream().sorted().toList();
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("功能编码不能为空");
        return value.trim();
    }
}
