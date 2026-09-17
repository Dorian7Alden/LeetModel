package com.leetmodel.evaluation.runner;

import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;

import java.math.BigDecimal;
import java.util.Map;

/** 每个 AI 功能独立实现的评价适配边界。 */
public interface EvaluationExperimentRunner {

    String featureCode();

    AiFeatureDefinitionDTO discoverFeature();

    ValidatedSamplePayload validateSample(EvaluationSamplePayloadDTO sample);

    AiExperimentResultDTO execute(EvaluationExperimentCommand command);

    EvaluationExperimentOutcome parseResult(EvaluationExperimentCommand command,
                                            AiExperimentResultDTO result);

    Map<String, BigDecimal> extractMetrics(EvaluationExperimentOutcome outcome);
}
