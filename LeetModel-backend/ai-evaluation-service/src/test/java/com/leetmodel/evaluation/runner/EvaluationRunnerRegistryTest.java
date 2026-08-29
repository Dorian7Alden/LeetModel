package com.leetmodel.evaluation.runner;

import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRunnerRegistryTest {

    @Test
    void fakeRunnerCanCoverFullSpiWithoutFeatureBranchesInRegistry() {
        FakeRunner fake = new FakeRunner();
        EvaluationRunnerRegistry registry = new EvaluationRunnerRegistry(List.of(fake));
        var runner = registry.require("FAKE");
        var sample = runner.validateSample(new EvaluationSamplePayloadDTO(
                "FAKE_SAMPLE", "FAKE_V1", "{\"value\":1}"));
        var command = new EvaluationExperimentCommand(
                "run-1", sample, "WORKFLOW_V1", "MODEL_V1", null, "P3");
        var raw = runner.execute(command);
        var outcome = runner.parseResult(command, raw);

        assertThat(runner.discoverFeature().getFeatureCode()).isEqualTo("FAKE");
        assertThat(outcome.aiCallId()).isEqualTo("call-1");
        assertThat(runner.extractMetrics(outcome)).containsEntry("FAKE_METRIC", BigDecimal.ONE);
    }

    @Test
    void unknownFeatureFailsExplicitly() {
        EvaluationRunnerRegistry registry = new EvaluationRunnerRegistry(List.of(new FakeRunner()));

        assertThatThrownBy(() -> registry.require("REVIEW"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知或未启用");
    }

    @Test
    void duplicateFeatureRegistrationFailsAtStartup() {
        assertThatThrownBy(() -> new EvaluationRunnerRegistry(List.of(new FakeRunner(), new FakeRunner())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复评价 Runner");
    }

    private static final class FakeRunner implements EvaluationExperimentRunner {

        @Override
        public String featureCode() {
            return "FAKE";
        }

        @Override
        public AiFeatureDefinitionDTO discoverFeature() {
            return new AiFeatureDefinitionDTO("FAKE", "Fake", "test", List.of("FAKE_SAMPLE"),
                    List.of("FAKE_METRIC"), List.of());
        }

        @Override
        public ValidatedSamplePayload validateSample(EvaluationSamplePayloadDTO sample) {
            return new ValidatedSamplePayload(sample.getSampleType(), sample.getPayloadSchemaVersion(),
                    sample.getPayloadJson(), null);
        }

        @Override
        public AiExperimentResultDTO execute(EvaluationExperimentCommand command) {
            return new AiExperimentResultDTO(command.experimentRunId(), "FAKE",
                    command.workflowVersion(), command.modelExecutionConfigVersion(), null,
                    "SUCCEEDED", null, "FAKE_OUTPUT_V1", "{\"answer\":true}",
                    "FAKE_METRICS_V1", "{\"value\":1}", "model", "call-1", 10L, null);
        }

        @Override
        public EvaluationExperimentOutcome parseResult(EvaluationExperimentCommand command,
                                                       AiExperimentResultDTO result) {
            return new EvaluationExperimentOutcome(result.getExperimentRunId(), result.getFeatureCode(),
                    result.getWorkflowVersion(), result.getModelExecutionConfigVersion(),
                    result.getRagIndexVersion(), result.getStatus(), result.getFailureType(),
                    result.getOutputJson(), result.getModelName(), result.getAiCallId(),
                    result.getDurationMs(), result.getErrorMessage(), Map.of("FAKE_METRIC", "1"));
        }

        @Override
        public Map<String, BigDecimal> extractMetrics(EvaluationExperimentOutcome outcome) {
            return Map.of("FAKE_METRIC", new BigDecimal(outcome.rawMetrics().get("FAKE_METRIC")));
        }
    }
}
