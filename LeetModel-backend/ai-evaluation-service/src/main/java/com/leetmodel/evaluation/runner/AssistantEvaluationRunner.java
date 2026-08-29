package com.leetmodel.evaluation.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiExperimentSampleDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;
import com.leetmodel.evaluation.service.EvaluationMetricRegistry;
import com.leetmodel.evaluation.service.EvaluationSamplePayloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** ASSISTANT 单轮隔离实验适配器，只保存回答的非敏感摘要。 */
@Component
@RequiredArgsConstructor
public class AssistantEvaluationRunner implements EvaluationExperimentRunner {

    private static final String FEATURE = "ASSISTANT";
    private static final String STRUCTURE_METRIC = "STRUCTURE_VALID_RATE";

    private final AssistantFeignClient assistantFeignClient;
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
            Result<AiFeatureDefinitionDTO> response = assistantFeignClient.getFeatureDefinition();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new EvaluationRunnerException("ENVIRONMENT", "客服功能目录暂不可用");
            }
            return response.getData();
        } catch (EvaluationRunnerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EvaluationRunnerException("ENVIRONMENT", "客服功能目录暂不可用", exception);
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
        var request = new AiExperimentRequestDTO(command.experimentRunId(), FEATURE,
                new AiExperimentSampleDTO(command.sample().sampleType(),
                        command.sample().payloadSchemaVersion(), command.sample().payloadJson()),
                command.workflowVersion(), command.modelExecutionConfigVersion(),
                command.ragIndexVersion(), command.priority());
        try {
            Result<AiExperimentResultDTO> response = assistantFeignClient.runExperiment(request);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new EvaluationRunnerException("ENVIRONMENT", "客服实验依赖暂不可用");
            }
            return response.getData();
        } catch (EvaluationRunnerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EvaluationRunnerException("ENVIRONMENT", "客服实验依赖暂不可用", exception);
        }
    }

    @Override
    public EvaluationExperimentOutcome parseResult(EvaluationExperimentCommand command,
                                                   AiExperimentResultDTO result) {
        if (!identityMatches(command, result)) {
            return failure(command, "OUTPUT", "客服实验返回的运行身份或版本不匹配");
        }
        if (!"SUCCEEDED".equals(result.getStatus())) {
            String failureType = "CONFIGURATION".equals(result.getFailureType())
                    ? "CONFIGURATION" : "ENVIRONMENT";
            return new EvaluationExperimentOutcome(result.getExperimentRunId(), FEATURE,
                    result.getWorkflowVersion(), result.getModelExecutionConfigVersion(),
                    result.getRagIndexVersion(), "FAILED", failureType, null,
                    result.getModelName(), result.getAiCallId(), result.getDurationMs(),
                    defaultMessage(result.getErrorMessage()), Map.of());
        }
        String answer = answer(result.getOutputJson());
        if (answer == null || result.getAiCallId() == null || result.getDurationMs() == null) {
            return failure(command, "OUTPUT", "客服版本未产生符合契约的结果");
        }
        return new EvaluationExperimentOutcome(result.getExperimentRunId(), FEATURE,
                result.getWorkflowVersion(), result.getModelExecutionConfigVersion(),
                result.getRagIndexVersion(), "SUCCEEDED", null, answerSummary(answer),
                result.getModelName(), result.getAiCallId(), result.getDurationMs(), null,
                Map.of(STRUCTURE_METRIC, "100"));
    }

    @Override
    public Map<String, BigDecimal> extractMetrics(EvaluationExperimentOutcome outcome) {
        String value = outcome.rawMetrics().get(STRUCTURE_METRIC);
        if (value == null) return Map.of();
        metricRegistry.requireApplicable(STRUCTURE_METRIC, FEATURE);
        return Map.of(STRUCTURE_METRIC, new BigDecimal(value));
    }

    private boolean identityMatches(EvaluationExperimentCommand command, AiExperimentResultDTO result) {
        return result != null
                && command.experimentRunId().equals(result.getExperimentRunId())
                && FEATURE.equals(result.getFeatureCode())
                && command.workflowVersion().equals(result.getWorkflowVersion())
                && command.modelExecutionConfigVersion().equals(result.getModelExecutionConfigVersion())
                && java.util.Objects.equals(command.ragIndexVersion(), result.getRagIndexVersion());
    }

    private String answer(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) return null;
        try {
            JsonNode value = objectMapper.readTree(outputJson).get("answer");
            return value == null || !value.isTextual() || value.textValue().isBlank()
                    ? null : value.textValue();
        } catch (Exception exception) {
            return null;
        }
    }

    private String answerSummary(String answer) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(answer.getBytes(StandardCharsets.UTF_8)));
            return objectMapper.writeValueAsString(Map.of(
                    "answerSha256", hash, "answerLength", answer.length(),
                    "answerStored", false));
        } catch (Exception exception) {
            throw new EvaluationRunnerException("OUTPUT", "客服回答摘要生成失败", exception);
        }
    }

    private EvaluationExperimentOutcome failure(EvaluationExperimentCommand command,
                                                String type, String message) {
        return new EvaluationExperimentOutcome(command.experimentRunId(), FEATURE,
                command.workflowVersion(), command.modelExecutionConfigVersion(),
                command.ragIndexVersion(), "FAILED", type, null, null, null, 0L,
                message, Map.of());
    }

    private String defaultMessage(String message) {
        return message == null || message.isBlank() ? "客服实验未能按锁定配置完成" : message;
    }
}
