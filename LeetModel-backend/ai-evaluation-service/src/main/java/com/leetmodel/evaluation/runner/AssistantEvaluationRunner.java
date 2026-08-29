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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ASSISTANT 单轮隔离实验适配器，只保存回答的非敏感摘要。 */
@Component
@RequiredArgsConstructor
public class AssistantEvaluationRunner implements EvaluationExperimentRunner {

    private static final String FEATURE = "ASSISTANT";
    private static final String STRUCTURE_METRIC = "STRUCTURE_VALID_RATE";
    private static final String RETRIEVAL_METRIC = "RETRIEVAL_HIT_RATE";
    private static final String SOURCE_METRIC = "SOURCE_COVERAGE_RATE";
    private static final String FORMAT_METRIC = "FORMAT_RULE_PASS_RATE";
    private static final String EXPECTED_POINT_METRIC = "EXPECTED_POINT_COVERAGE_RATE";

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
                command.ragIndexVersion(), command.priority(), command.evaluationTaskId(),
                command.slotKey(), command.attemptNo(), command.idempotencyKey());
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
        if ("PENDING".equals(result.getStatus()) || "UNKNOWN".equals(result.getStatus())) {
            return new EvaluationExperimentOutcome(result.getExperimentRunId(), FEATURE,
                    result.getWorkflowVersion(), result.getModelExecutionConfigVersion(),
                    result.getRagIndexVersion(), result.getStatus(),
                    "UNKNOWN".equals(result.getStatus()) ? "UNKNOWN" : null,
                    null, result.getModelName(), result.getAiCallId(), result.getDurationMs(),
                    defaultMessage(result.getErrorMessage()), Map.of());
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
        JsonNode output = output(result.getOutputJson());
        String answer = output == null ? null : text(output.get("answer"));
        if (answer == null || result.getAiCallId() == null || result.getDurationMs() == null) {
            return failure(command, "OUTPUT", "客服版本未产生符合契约的结果");
        }
        Map<String, String> metrics = verifiableMetrics(command, output, answer);
        return new EvaluationExperimentOutcome(result.getExperimentRunId(), FEATURE,
                result.getWorkflowVersion(), result.getModelExecutionConfigVersion(),
                result.getRagIndexVersion(), "SUCCEEDED", null, answerSummary(answer),
                result.getModelName(), result.getAiCallId(), result.getDurationMs(), null,
                Map.copyOf(metrics));
    }

    @Override
    public Map<String, BigDecimal> extractMetrics(EvaluationExperimentOutcome outcome) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        outcome.rawMetrics().forEach((code, value) -> {
            metricRegistry.requireApplicable(code, FEATURE);
            values.put(code, new BigDecimal(value));
        });
        return Map.copyOf(values);
    }

    private boolean identityMatches(EvaluationExperimentCommand command, AiExperimentResultDTO result) {
        return result != null
                && command.experimentRunId().equals(result.getExperimentRunId())
                && FEATURE.equals(result.getFeatureCode())
                && command.workflowVersion().equals(result.getWorkflowVersion())
                && command.modelExecutionConfigVersion().equals(result.getModelExecutionConfigVersion())
                && java.util.Objects.equals(command.ragIndexVersion(), result.getRagIndexVersion());
    }

    private JsonNode output(String outputJson) {
        if (outputJson == null || outputJson.isBlank()) return null;
        try {
            JsonNode value = objectMapper.readTree(outputJson);
            return value.isObject() ? value : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private Map<String, String> verifiableMetrics(EvaluationExperimentCommand command,
                                                   JsonNode output, String answer) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(STRUCTURE_METRIC, "100");
        JsonNode sample = readObject(command.sample().payloadJson());
        if ("ASSISTANT_RAG_V1".equals(command.workflowVersion())) {
            if (output.has("retrievedChunkCount") && output.get("retrievedChunkCount").canConvertToInt()) {
                int count = output.get("retrievedChunkCount").asInt();
                values.put(RETRIEVAL_METRIC, count > 0 ? "100" : "0");
            }
            List<String> expectedSources = strings(sample.get("expectedSources"));
            if (!expectedSources.isEmpty() && output.has("retrievedSourcePaths")
                    && output.get("retrievedSourcePaths").isArray()) {
                Set<String> actual = new HashSet<>(strings(output.get("retrievedSourcePaths")));
                long covered = expectedSources.stream().distinct().filter(actual::contains).count();
                values.put(SOURCE_METRIC, percent(covered, expectedSources.stream().distinct().count()));
            }
        }
        List<String> rules = strings(sample.get("formatRules"));
        if (!rules.isEmpty()) {
            long passed = rules.stream().filter(rule -> passes(rule, answer)).count();
            values.put(FORMAT_METRIC, percent(passed, rules.size()));
        }
        List<String> expectedPoints = strings(sample.get("expectedPoints"));
        if (!expectedPoints.isEmpty()) {
            String normalizedAnswer = normalize(answer);
            long covered = expectedPoints.stream().filter(point -> normalizedAnswer.contains(normalize(point))).count();
            values.put(EXPECTED_POINT_METRIC, percent(covered, expectedPoints.size()));
        }
        return values;
    }

    private boolean passes(String rule, String answer) {
        return switch (rule) {
            case "ANSWER_NON_BLANK" -> !answer.isBlank();
            case "ANSWER_MAX_2000" -> answer.length() <= 2000;
            case "NO_MARKDOWN_CODE_FENCE" -> !answer.contains("```");
            case "REQUIRES_SOURCE_MARKER" -> answer.contains("来源") || answer.contains("[");
            default -> false;
        };
    }

    private JsonNode readObject(String json) {
        try {
            JsonNode value = objectMapper.readTree(json);
            return value.isObject() ? value : objectMapper.createObjectNode();
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> strings(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        value.forEach(item -> { if (item.isTextual() && !item.textValue().isBlank()) result.add(item.textValue()); });
        return List.copyOf(result);
    }

    private String text(JsonNode value) {
        return value == null || !value.isTextual() || value.textValue().isBlank() ? null : value.textValue();
    }

    private String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    private String percent(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, java.math.RoundingMode.HALF_UP).toPlainString();
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
