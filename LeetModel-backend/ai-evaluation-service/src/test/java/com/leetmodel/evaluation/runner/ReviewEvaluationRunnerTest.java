package com.leetmodel.evaluation.runner;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
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

class ReviewEvaluationRunnerTest {

    private ReviewFeignClient client;
    private ReviewEvaluationRunner runner;

    @BeforeEach
    void setUp() {
        client = mock(ReviewFeignClient.class);
        var mapper = JsonMapper.builder().build();
        runner = new ReviewEvaluationRunner(client, new EvaluationSamplePayloadService(mapper),
                new EvaluationMetricRegistry(), mapper);
    }

    @Test
    void successUsesGenericContractAndExtractsReviewScore() {
        var sample = runner.validateSample(new EvaluationSamplePayloadDTO(
                "SUBMISSION_REFERENCE", "REVIEW_SUBMISSION_V1", "{\"submissionId\":31}"));
        var command = new EvaluationExperimentCommand(
                "review-eval:1:2:1", "1", "1:2:1", 1,
                "evaluation:1:1:2:1:attempt:1", sample, "BASIC_REVIEW_V1",
                "MODEL_CFG_REVIEW_MULTIMODAL_0001", null, "P3");
        when(client.runExperimentV2(org.mockito.ArgumentMatchers.any())).thenReturn(Result.ok(result(
                command, "SUCCEEDED", null, "{\"score\":88}", "{\"score\":88}", "call-1")));

        var outcome = runner.parseResult(command, runner.execute(command));

        var captor = ArgumentCaptor.forClass(com.leetmodel.common.api.dto.AiExperimentRequestDTO.class);
        verify(client).runExperimentV2(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo("P3");
        assertThat(captor.getValue().getEvaluationTaskId()).isEqualTo("1");
        assertThat(captor.getValue().getSlotKey()).isEqualTo("1:2:1");
        assertThat(captor.getValue().getAttemptNo()).isEqualTo(1);
        assertThat(captor.getValue().getIdempotencyKey())
                .isEqualTo("evaluation:1:1:2:1:attempt:1");
        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(runner.extractMetrics(outcome)).containsEntry("REVIEW_SCORE", new BigDecimal("88"));
    }

    @Test
    void malformedSuccessIsBusinessOutputFailure() {
        var command = command();
        var outcome = runner.parseResult(command,
                result(command, "SUCCEEDED", null, null, "{}", null));

        assertThat(outcome.status()).isEqualTo("FAILED");
        assertThat(outcome.failureType()).isEqualTo("OUTPUT");
    }

    @Test
    void dependencyFailureRemainsRetryableEnvironmentFailure() {
        when(client.runExperimentV2(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        assertThatThrownBy(() -> runner.execute(command()))
                .isInstanceOf(EvaluationRunnerException.class)
                .extracting("failureType").isEqualTo("ENVIRONMENT");
    }

    @Test
    void featureDiscoveryUsesGenericDefinition() {
        when(client.getFeatureDefinition()).thenReturn(Result.ok(new AiFeatureDefinitionDTO(
                "REVIEW", "论文评审", "ai-review-service", List.of("REVIEW_SUBMISSION_V1"),
                List.of("score"), List.of())));

        assertThat(runner.discoverFeature().getFeatureCode()).isEqualTo("REVIEW");
    }

    private EvaluationExperimentCommand command() {
        return new EvaluationExperimentCommand("review-eval:1:2:1",
                new com.leetmodel.evaluation.model.ValidatedSamplePayload(
                        "SUBMISSION_REFERENCE", "REVIEW_SUBMISSION_V1", "{\"submissionId\":31}", 31L),
                "BASIC_REVIEW_V1", "MODEL_CFG_REVIEW_MULTIMODAL_0001", null, "P3");
    }

    private AiExperimentResultDTO result(EvaluationExperimentCommand command, String status,
                                         String failureType, String output, String metrics,
                                         String callId) {
        return new AiExperimentResultDTO(command.experimentRunId(), "REVIEW", command.workflowVersion(),
                command.modelExecutionConfigVersion(), null, status, failureType,
                "REVIEW_OUTPUT_V1", output, "REVIEW_METRICS_V1", metrics,
                "model", callId, 50L, failureType == null ? null : "failed");
    }
}
