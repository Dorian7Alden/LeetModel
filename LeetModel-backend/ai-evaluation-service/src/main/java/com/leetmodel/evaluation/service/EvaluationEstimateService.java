package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.EvaluationCandidateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.evaluation.config.EvaluationScaleProperties;
import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.enums.EvaluationErrorCode;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.runner.EvaluationExperimentRunner;
import com.leetmodel.evaluation.runner.EvaluationRunnerException;
import com.leetmodel.evaluation.runner.EvaluationRunnerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 在创建前以同一套服务端限制计算槽位、调用量和费用完整性。 */
@Service
@RequiredArgsConstructor
public class EvaluationEstimateService {

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationRunnerRegistry runnerRegistry;
    private final EvaluationScaleProperties limits;

    public EvaluationEstimateDTO estimate(EvaluationEstimateRequestDTO request) {
        EvaluationDataset dataset = datasetMapper.selectById(request.getDatasetId());
        BusinessException.throwIf(dataset == null, EvaluationErrorCode.DATASET_NOT_FOUND);
        String featureCode = dataset.getFeatureCode() == null ? "REVIEW" : dataset.getFeatureCode();
        EvaluationExperimentRunner runner = runnerRegistry.require(featureCode);
        AiFeatureDefinitionDTO feature = discover(runner);
        validateCandidates(featureCode, feature, request.getCandidates());

        int sampleCount = dataset.getSampleCount() == null ? 0 : dataset.getSampleCount();
        int versionCount = request.getCandidates().size();
        long totalSlots = Math.multiplyExact(Math.multiplyExact((long) sampleCount, versionCount),
                request.getRepeatCount());
        long estimatedCalls = request.getCandidates().stream()
                .mapToLong(candidate -> callsPerSlot(featureCode, candidate))
                .map(calls -> Math.multiplyExact(calls,
                        Math.multiplyExact((long) sampleCount, request.getRepeatCount())))
                .sum();
        List<String> violations = violations(sampleCount, versionCount,
                request.getRepeatCount(), totalSlots, estimatedCalls);
        return new EvaluationEstimateDTO(dataset.getId(), dataset.getDatasetVersion(), featureCode,
                sampleCount, versionCount, request.getRepeatCount(), totalSlots, estimatedCalls,
                "P3", null, null, "UNAVAILABLE",
                "创建前缺少真实 Token 用量和可追溯价格快照，费用不能可靠估算；运行后按 callId 聚合实际或明确估算费用",
                violations.isEmpty(), violations);
    }

    public void requireWithinLimits(EvaluationEstimateRequestDTO request) {
        BusinessException.throwIf(!estimate(request).getWithinLimits(),
                EvaluationErrorCode.SCALE_LIMIT_EXCEEDED);
    }

    public void requireDatasetSize(int sampleCount) {
        BusinessException.throwIf(sampleCount > limits.getMaxSamples(),
                EvaluationErrorCode.SCALE_LIMIT_EXCEEDED);
    }

    private AiFeatureDefinitionDTO discover(EvaluationExperimentRunner runner) {
        try {
            return runner.discoverFeature();
        } catch (EvaluationRunnerException exception) {
            throw new BusinessException(EvaluationErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void validateCandidates(String featureCode, AiFeatureDefinitionDTO feature,
                                    List<EvaluationCandidateDTO> candidates) {
        Set<String> keys = new HashSet<>();
        for (EvaluationCandidateDTO candidate : candidates) {
            String rag = trim(candidate.getRagIndexVersion());
            String key = candidate.getWorkflowVersion().trim() + "|"
                    + candidate.getModelExecutionConfigVersion().trim() + "|" + rag;
            BusinessException.throwIf(!keys.add(key), EvaluationErrorCode.DUPLICATE_CANDIDATE);
            boolean enabled = feature.getWorkflowVersions() != null
                    && feature.getWorkflowVersions().stream().anyMatch(version ->
                    candidate.getWorkflowVersion().equals(version.getWorkflowVersion())
                            && "ENABLED".equals(version.getStatus()));
            BusinessException.throwIf(!enabled || !executionMatches(featureCode, candidate, rag),
                    EvaluationErrorCode.VERSION_UNAVAILABLE);
        }
    }

    private boolean executionMatches(String featureCode, EvaluationCandidateDTO candidate, String rag) {
        if ("REVIEW".equals(featureCode)) {
            return "MODEL_CFG_REVIEW_MULTIMODAL_0001".equals(
                    candidate.getModelExecutionConfigVersion()) && rag == null;
        }
        if (!"ASSISTANT".equals(featureCode)
                || !"MODEL_CFG_ASSISTANT_TEXT_0001".equals(candidate.getModelExecutionConfigVersion())) {
            return false;
        }
        return "ASSISTANT_RAG_V1".equals(candidate.getWorkflowVersion())
                ? rag != null : "ASSISTANT_NO_RAG_V1".equals(candidate.getWorkflowVersion()) && rag == null;
    }

    private long callsPerSlot(String featureCode, EvaluationCandidateDTO candidate) {
        return "ASSISTANT".equals(featureCode)
                && "ASSISTANT_RAG_V1".equals(candidate.getWorkflowVersion()) ? 2L : 1L;
    }

    private List<String> violations(int samples, int versions, int repeats,
                                    long slots, long calls) {
        List<String> values = new ArrayList<>();
        if (samples > limits.getMaxSamples()) values.add("样本数超过 " + limits.getMaxSamples());
        if (versions > limits.getMaxVersions()) values.add("候选版本数超过 " + limits.getMaxVersions());
        if (repeats > limits.getMaxRepeatCount()) values.add("重复次数超过 " + limits.getMaxRepeatCount());
        if (slots > limits.getMaxTotalSlots()) values.add("总槽位超过 " + limits.getMaxTotalSlots());
        if (calls > limits.getMaxEstimatedCalls()) {
            values.add("预计模型调用量超过 " + limits.getMaxEstimatedCalls());
        }
        return List.copyOf(values);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
