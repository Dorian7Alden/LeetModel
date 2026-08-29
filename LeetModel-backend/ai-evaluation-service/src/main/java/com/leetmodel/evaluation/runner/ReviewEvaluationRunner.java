package com.leetmodel.evaluation.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiExperimentSampleDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;
import com.leetmodel.evaluation.service.EvaluationMetricRegistry;
import com.leetmodel.evaluation.service.EvaluationSamplePayloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** REVIEW 通用实验适配器，不依赖旧 Review 实验 DTO。 */
@Component
@RequiredArgsConstructor
public class ReviewEvaluationRunner implements EvaluationExperimentRunner {

    private static final String FEATURE = "REVIEW";
    private static final String METRIC_SCORE = "REVIEW_SCORE";

    private final ReviewFeignClient reviewFeignClient;
    private final EvaluationSamplePayloadService payloadService;
    private final EvaluationMetricRegistry metricRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public String featureCode() {
        return FEATURE;
    }

    @Override
    public AiFeatureDefinitionDTO discoverFeature() {
        try {
            Result<AiFeatureDefinitionDTO> response = reviewFeignClient.getFeatureDefinition();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new EvaluationRunnerException("ENVIRONMENT", "评审功能目录暂不可用");
            }
            return response.getData();
        } catch (EvaluationRunnerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EvaluationRunnerException("ENVIRONMENT", "评审功能目录暂不可用", exception);
        }
    }

    @Override
    public ValidatedSamplePayload validateSample(EvaluationSamplePayloadDTO sample) {
        try {
            return payloadService.validate(FEATURE, sample);
        } catch (IllegalArgumentException exception) {
            throw new EvaluationRunnerException("CONFIGURATION", exception.getMessage(), exception);
        }
    }

    @Override
    public AiExperimentResultDTO execute(EvaluationExperimentCommand command) {
        AiExperimentRequestDTO request = new AiExperimentRequestDTO(
                command.experimentRunId(), FEATURE,
                new AiExperimentSampleDTO(command.sample().sampleType(),
                        command.sample().payloadSchemaVersion(), command.sample().payloadJson()),
                command.workflowVersion(), command.modelExecutionConfigVersion(),
                command.ragIndexVersion(), command.priority());
        try {
            Result<AiExperimentResultDTO> response = reviewFeignClient.runExperimentV2(request);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new EvaluationRunnerException("ENVIRONMENT", "实验评审依赖暂不可用");
            }
            return response.getData();
        } catch (EvaluationRunnerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EvaluationRunnerException("ENVIRONMENT", "实验评审依赖暂不可用", exception);
        }
    }

    @Override
    public EvaluationExperimentOutcome parseResult(EvaluationExperimentCommand command,
                                                   AiExperimentResultDTO result) {
        if (!identityMatches(command, result)) {
            return failure(command, "OUTPUT", "评审实验返回的运行身份或版本不匹配");
        }
        if (!"SUCCEEDED".equals(result.getStatus())) {
            String type = "ENVIRONMENT".equals(result.getFailureType())
                    || "CONFIGURATION".equals(result.getFailureType())
                    ? result.getFailureType() : "OUTPUT";
            return new EvaluationExperimentOutcome(result.getExperimentRunId(), FEATURE,
                    result.getWorkflowVersion(), result.getModelExecutionConfigVersion(), null,
                    "FAILED", type, null, result.getModelName(), result.getAiCallId(),
                    result.getDurationMs(), defaultMessage(result.getErrorMessage()), Map.of());
        }
        BigDecimal score = score(result.getMetricsJson());
        if (score == null || result.getOutputJson() == null || result.getDurationMs() == null) {
            return failure(command, "OUTPUT", "评审版本未产生符合契约的结果");
        }
        return new EvaluationExperimentOutcome(result.getExperimentRunId(), FEATURE,
                result.getWorkflowVersion(), result.getModelExecutionConfigVersion(), null,
                "SUCCEEDED", null, result.getOutputJson(), result.getModelName(),
                result.getAiCallId(), result.getDurationMs(), null,
                Map.of(METRIC_SCORE, score.toPlainString()));
    }

    @Override
    public Map<String, BigDecimal> extractMetrics(EvaluationExperimentOutcome outcome) {
        String value = outcome.rawMetrics().get(METRIC_SCORE);
        if (value == null) return Map.of();
        metricRegistry.requireApplicable(METRIC_SCORE, FEATURE);
        return Map.of(METRIC_SCORE, new BigDecimal(value));
    }

    private boolean identityMatches(EvaluationExperimentCommand command, AiExperimentResultDTO result) {
        return result != null
                && command.experimentRunId().equals(result.getExperimentRunId())
                && FEATURE.equals(result.getFeatureCode())
                && command.workflowVersion().equals(result.getWorkflowVersion())
                && command.modelExecutionConfigVersion().equals(result.getModelExecutionConfigVersion())
                && result.getRagIndexVersion() == null;
    }

    private BigDecimal score(String metricsJson) {
        if (metricsJson == null || metricsJson.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metricsJson).get("score");
            return node == null || !node.isNumber() ? null : node.decimalValue();
        } catch (Exception exception) {
            return null;
        }
    }

    private EvaluationExperimentOutcome failure(EvaluationExperimentCommand command,
                                                String failureType, String message) {
        return new EvaluationExperimentOutcome(command.experimentRunId(), FEATURE,
                command.workflowVersion(), command.modelExecutionConfigVersion(), null,
                "FAILED", failureType, null, null, null, 0L, message, Map.of());
    }

    private String defaultMessage(String message) {
        return message == null || message.isBlank() ? "评审版本未产生符合契约的结果" : message;
    }
}
