package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.AiWorkflowVersionDTO;
import com.leetmodel.common.api.dto.EvaluationCandidateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.evaluation.config.EvaluationScaleProperties;
import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.runner.EvaluationExperimentCommand;
import com.leetmodel.evaluation.runner.EvaluationExperimentOutcome;
import com.leetmodel.evaluation.runner.EvaluationExperimentRunner;
import com.leetmodel.evaluation.runner.EvaluationRunnerRegistry;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationEstimateServiceTest {

    private EvaluationDatasetMapper datasetMapper;
    private EvaluationScaleProperties limits;
    private EvaluationEstimateService service;

    @BeforeEach
    void setUp() {
        datasetMapper = mock(EvaluationDatasetMapper.class);
        limits = new EvaluationScaleProperties();
        service = new EvaluationEstimateService(datasetMapper,
                new EvaluationRunnerRegistry(List.of(new FeatureRunner())), limits);
    }

    @Test
    void estimatesSlotsCallsAndExplicitlyUnavailableCostBeforeCreation() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10, "ASSISTANT"));
        var result = service.estimate(new EvaluationEstimateRequestDTO(10L, List.of(
                candidate("ASSISTANT_NO_RAG_V1", null),
                candidate("ASSISTANT_RAG_V1", "rag-v1")), 4));

        assertThat(result.getTotalSlots()).isEqualTo(80L);
        assertThat(result.getEstimatedCallCount()).isEqualTo(120L);
        assertThat(result.getPriority()).isEqualTo("P3");
        assertThat(result.getEstimatedCostAmount()).isNull();
        assertThat(result.getCostCompleteness()).isEqualTo("UNAVAILABLE");
        assertThat(result.getWithinLimits()).isTrue();
    }

    @Test
    void overLimitIsVisibleInEstimateAndRejectedForCreation() {
        limits.setMaxRepeatCount(3);
        when(datasetMapper.selectById(10L)).thenReturn(dataset(2, "ASSISTANT"));
        var request = new EvaluationEstimateRequestDTO(10L,
                List.of(candidate("ASSISTANT_NO_RAG_V1", null)), 4);

        assertThat(service.estimate(request).getViolations()).contains("重复次数超过 3");
        assertThatThrownBy(() -> service.requireWithinLimits(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41108);
    }

    @Test
    void duplicateCandidatesAreRejected() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(2, "ASSISTANT"));
        var candidate = candidate("ASSISTANT_NO_RAG_V1", null);

        assertThatThrownBy(() -> service.estimate(new EvaluationEstimateRequestDTO(
                10L, List.of(candidate, candidate), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41109);
    }

    @Test
    void ragCandidateRequiresExactIndexAndFixedModelConfig() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(2, "ASSISTANT"));

        assertThatThrownBy(() -> service.estimate(new EvaluationEstimateRequestDTO(10L,
                List.of(candidate("ASSISTANT_RAG_V1", null)), 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41104);
    }

    private EvaluationDataset dataset(int samples, String feature) {
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setId(10L);
        dataset.setDatasetVersion("DATASET_V1");
        dataset.setFeatureCode(feature);
        dataset.setSampleCount(samples);
        return dataset;
    }

    private EvaluationCandidateDTO candidate(String workflow, String rag) {
        return new EvaluationCandidateDTO(workflow, "MODEL_CFG_ASSISTANT_TEXT_0001", rag);
    }

    private static final class FeatureRunner implements EvaluationExperimentRunner {
        @Override public String featureCode() { return "ASSISTANT"; }
        @Override public AiFeatureDefinitionDTO discoverFeature() {
            return new AiFeatureDefinitionDTO("ASSISTANT", "客服", "assistant",
                    List.of("QUESTION"), List.of(), List.of(
                    version("ASSISTANT_NO_RAG_V1"), version("ASSISTANT_RAG_V1")));
        }
        private AiWorkflowVersionDTO version(String code) {
            return new AiWorkflowVersionDTO(code, code, "ENABLED", "IN", "OUT", "兼容");
        }
        @Override public ValidatedSamplePayload validateSample(EvaluationSamplePayloadDTO sample) {
            throw new UnsupportedOperationException();
        }
        @Override public AiExperimentResultDTO execute(EvaluationExperimentCommand command) {
            throw new UnsupportedOperationException();
        }
        @Override public EvaluationExperimentOutcome parseResult(
                EvaluationExperimentCommand command, AiExperimentResultDTO result) {
            throw new UnsupportedOperationException();
        }
        @Override public Map<String, BigDecimal> extractMetrics(EvaluationExperimentOutcome outcome) {
            return Map.of();
        }
    }
}
