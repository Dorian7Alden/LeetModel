package com.leetmodel.evaluation.runner;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.feign.SuggestionFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.service.EvaluationMetricRegistry;
import com.leetmodel.evaluation.service.EvaluationSamplePayloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionEvaluationRunnerTest {

    private SuggestionFeignClient client;
    private SuggestionEvaluationRunner runner;

    @BeforeEach
    void setUp() {
        client = mock(SuggestionFeignClient.class);
        var mapper = JsonMapper.builder().build();
        runner = new SuggestionEvaluationRunner(client, new EvaluationSamplePayloadService(mapper),
                new EvaluationMetricRegistry(), mapper);
    }

    @Test
    void successUsesGenericContractAndExtractsStructureValidRate() {
        var sample = runner.validateSample(new EvaluationSamplePayloadDTO(
                "SUBMISSION_REFERENCE", "SUGGESTION_SUBMISSION_V1", "{\"submissionId\":31}"));
        var command = new EvaluationExperimentCommand(
                "suggestion-eval:1:2:1", "1", "1:2:1", 1,
                "evaluation:1:1:2:1:attempt:1", sample, "IMPROVEMENT_V1",
                "MODEL_CFG_SUGGESTION_TEXT_0001", null, "P3");
        when(client.runExperiment(org.mockito.ArgumentMatchers.any())).thenReturn(Result.ok(result(
                command, "SUCCEEDED", null, "{\"summary\":\"建议\"}", "call-1")));

        var outcome = runner.parseResult(command, runner.execute(command));

        var captor = ArgumentCaptor.forClass(com.leetmodel.common.api.dto.AiExperimentRequestDTO.class);
        verify(client).runExperiment(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo("P3");
        assertThat(captor.getValue().getEvaluationTaskId()).isEqualTo("1");
        assertThat(captor.getValue().getSlotKey()).isEqualTo("1:2:1");
        assertThat(captor.getValue().getAttemptNo()).isEqualTo(1);
        assertThat(captor.getValue().getIdempotencyKey())
                .isEqualTo("evaluation:1:1:2:1:attempt:1");
        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(outcome.outputSummaryJson()).contains("建议");
        assertThat(runner.extractMetrics(outcome)).containsEntry("STRUCTURE_VALID_RATE", new BigDecimal("100"));
    }

    @Test
    void malformedSuccessIsBusinessOutputFailure() {
        var command = command();
        var outcome = runner.parseResult(command,
                result(command, "SUCCEEDED", null, null, null));

        assertThat(outcome.status()).isEqualTo("FAILED");
        assertThat(outcome.failureType()).isEqualTo("OUTPUT");
    }

    @Test
    void dependencyFailureRemainsRetryableEnvironmentFailure() {
        when(client.runExperiment(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        assertThatThrownBy(() -> runner.execute(command()))
                .isInstanceOf(EvaluationRunnerException.class)
                .extracting("failureType").isEqualTo("ENVIRONMENT");
    }

    @Test
    void featureDiscoveryUsesGenericDefinition() {
        when(client.getFeatureDefinition()).thenReturn(Result.ok(new AiFeatureDefinitionDTO(
                "SUGGESTION", "AI 论文建议", "ai-suggestion-service", List.of("SUGGESTION_SUBMISSION_V1"),
                List.of("STRUCTURE_VALID_RATE"), List.of())));

        assertThat(runner.discoverFeature().getFeatureCode()).isEqualTo("SUGGESTION");
    }

    private EvaluationExperimentCommand command() {
        return new EvaluationExperimentCommand("suggestion-eval:1:2:1",
                new com.leetmodel.evaluation.model.ValidatedSamplePayload(
                        "SUBMISSION_REFERENCE", "SUGGESTION_SUBMISSION_V1", "{\"submissionId\":31}", 31L),
                "IMPROVEMENT_V1", "MODEL_CFG_SUGGESTION_TEXT_0001", null, "P3");
    }

    private AiExperimentResultDTO result(EvaluationExperimentCommand command, String status,
                                         String failureType, String output, String callId) {
        return new AiExperimentResultDTO(command.experimentRunId(), "SUGGESTION", command.workflowVersion(),
                command.modelExecutionConfigVersion(), null, status, failureType,
                "SUGGESTION_RESULT_V1", output, "SUGGESTION_RUN_METRICS_V1", "{}",
                "model", callId, 50L, failureType == null ? null : "failed");
    }
}
