package com.leetmodel.evaluation.runner;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.service.EvaluationMetricRegistry;
import com.leetmodel.evaluation.service.EvaluationSamplePayloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantEvaluationRunnerTest {

    private AssistantFeignClient client;
    private AssistantEvaluationRunner runner;

    @BeforeEach
    void setUp() {
        client = mock(AssistantFeignClient.class);
        var mapper = JsonMapper.builder().build();
        runner = new AssistantEvaluationRunner(client, new EvaluationSamplePayloadService(mapper),
                new EvaluationMetricRegistry(), mapper);
    }

    @Test
    void noRagRunKeepsCallIdButStoresOnlyAnswerDigest() {
        var command = command("ASSISTANT_NO_RAG_V1", null);
        when(client.runExperiment(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Result.ok(result(command, "敏感的完整客服回答")));

        var outcome = runner.parseResult(command, runner.execute(command));

        assertThat(outcome.aiCallId()).isEqualTo("call-assistant");
        assertThat(outcome.ragIndexVersion()).isNull();
        assertThat(outcome.outputSummaryJson()).contains("answerSha256", "answerLength")
                .contains("\"answerStored\":false")
                .doesNotContain("敏感的完整客服回答");
    }

    @Test
    void ragRunLocksAndReturnsExactIndexVersionAtP3() {
        var command = command("ASSISTANT_RAG_V1", "rag-v1-abc",
                "{\"question\":\"如何提交论文？\",\"expectedSources\":[\"docs/submit.md\"],"
                        + "\"expectedPoints\":[\"只接受 PDF\"],"
                        + "\"formatRules\":[\"ANSWER_NON_BLANK\",\"NO_MARKDOWN_CODE_FENCE\"]}");
        when(client.runExperiment(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Result.ok(result(command, "只接受 PDF，来源见说明", 2,
                        "docs/submit.md")));

        var outcome = runner.parseResult(command, runner.execute(command));

        var captor = ArgumentCaptor.forClass(com.leetmodel.common.api.dto.AiExperimentRequestDTO.class);
        verify(client).runExperiment(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo("P3");
        assertThat(captor.getValue().getRagIndexVersion()).isEqualTo("rag-v1-abc");
        assertThat(outcome.ragIndexVersion()).isEqualTo("rag-v1-abc");
        assertThat(runner.extractMetrics(outcome)).containsEntry("STRUCTURE_VALID_RATE",
                        new java.math.BigDecimal("100"))
                .containsEntry("RETRIEVAL_HIT_RATE", new java.math.BigDecimal("100"))
                .containsEntry("SOURCE_COVERAGE_RATE", new java.math.BigDecimal("100.00"))
                .containsEntry("FORMAT_RULE_PASS_RATE", new java.math.BigDecimal("100.00"))
                .containsEntry("EXPECTED_POINT_COVERAGE_RATE", new java.math.BigDecimal("100.00"));
    }

    @Test
    void mismatchedReturnedIndexIsOutputFailure() {
        var command = command("ASSISTANT_RAG_V1", "rag-v1-abc");
        var result = result(command, "回答");
        result.setRagIndexVersion("rag-v1-other");

        assertThat(runner.parseResult(command, result).failureType()).isEqualTo("OUTPUT");
    }

    private EvaluationExperimentCommand command(String workflowVersion, String ragIndexVersion) {
        return command(workflowVersion, ragIndexVersion, "{\"question\":\"如何提交论文？\"}");
    }

    private EvaluationExperimentCommand command(String workflowVersion, String ragIndexVersion,
                                                String payloadJson) {
        var sample = runner.validateSample(new EvaluationSamplePayloadDTO(
                "QUESTION", "ASSISTANT_QUESTION_V1", payloadJson));
        return new EvaluationExperimentCommand("assistant-eval:1:2:1", sample, workflowVersion,
                "MODEL_CFG_ASSISTANT_TEXT_0001", ragIndexVersion, "P3");
    }

    private AiExperimentResultDTO result(EvaluationExperimentCommand command, String answer) {
        return result(command, answer, 0, null);
    }

    private AiExperimentResultDTO result(EvaluationExperimentCommand command, String answer,
                                         int retrievedCount, String source) {
        String sources = source == null ? "[]" : "[\"" + source + "\"]";
        return new AiExperimentResultDTO(command.experimentRunId(), "ASSISTANT",
                command.workflowVersion(), command.modelExecutionConfigVersion(),
                command.ragIndexVersion(), "SUCCEEDED", null, "ASSISTANT_REPLY_V1",
                "{\"answer\":\"" + answer + "\",\"retrievedChunkCount\":" + retrievedCount
                        + ",\"retrievedSourcePaths\":" + sources + "}",
                "ASSISTANT_RUN_METRICS_V1", "{}",
                "model", "call-assistant", 20L, null);
    }
}
