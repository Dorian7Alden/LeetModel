package com.leetmodel.suggestion.service.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 不调用模型地把历史结构化 V1 评语投影为稳定发现；原字段路径永久保留。 */
@Component
public class ReviewEvidenceProjector {
    public static final String LEGACY_PROJECTION_VERSION = "LEGACY_REVIEW_EVIDENCE_V1";
    private final ObjectMapper objectMapper;

    public ReviewEvidenceProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isNativeV2(ReviewSummaryDTO review) {
        return "EVIDENCE_REVIEW_V2".equals(review.getWorkflowVersion());
    }

    public boolean hasStructuredLegacyEvidence(ReviewSummaryDTO review) {
        try {
            JsonNode root = objectMapper.readTree(review.getResultJson());
            return nonEmpty(root.path("weaknesses")) || nonEmpty(root.path("suggestions"));
        } catch (Exception exception) {
            return false;
        }
    }

    public ReviewEvidenceSnapshot nativeV2(ReviewSummaryDTO eligibility,
                                           ReviewSummaryDTO evidenceReview) {
        try {
            JsonNode root = objectMapper.readTree(evidenceReview.getResultJson());
            Map<String, String> evidenceBlocks = new HashMap<>();
            for (JsonNode evidence : root.path("evidence")) {
                String blockId = evidence.path("blockId").asText(null);
                if (blockId != null && !blockId.isBlank()) {
                    evidenceBlocks.put(evidence.path("evidenceId").asText(), blockId);
                }
            }
            List<ReviewEvidenceSnapshot.Finding> findings = new ArrayList<>();
            for (JsonNode finding : root.path("findings")) {
                List<String> blocks = new ArrayList<>();
                for (JsonNode evidenceId : finding.path("evidenceIds")) {
                    String block = evidenceBlocks.get(evidenceId.asText());
                    if (block != null && !blocks.contains(block)) blocks.add(block);
                }
                findings.add(new ReviewEvidenceSnapshot.Finding(
                        finding.path("findingId").asText(), finding.path("type").asText(),
                        finding.path("category").asText(), finding.path("severity").asText(),
                        finding.path("statement").asText(), finding.path("scoreImpact").asText(),
                        "$.findings[?(@.findingId=='" + finding.path("findingId").asText() + "')]",
                        List.copyOf(blocks)));
            }
            if (findings.isEmpty()) throw new IllegalArgumentException("V2 评审没有结构化发现");
            return snapshot(eligibility, evidenceReview, null, findings);
        } catch (Exception exception) {
            throw new IllegalArgumentException("EVIDENCE_REVIEW_V2 结果无法形成建议依据", exception);
        }
    }

    public ReviewEvidenceSnapshot projectLegacy(ReviewSummaryDTO review) {
        try {
            JsonNode root = objectMapper.readTree(review.getResultJson());
            List<ReviewEvidenceSnapshot.Finding> findings = new ArrayList<>();
            append(findings, root.path("weaknesses"), "WEAKNESS", "ISSUE", "HIGH",
                    "LEGACY_REVIEW", "历史评审指出该问题会降低论文可信度或完整性");
            append(findings, root.path("suggestions"), "SUGGESTION", "ISSUE", "MEDIUM",
                    "LEGACY_REVIEW", "历史评审认为该方面需要改进");
            append(findings, root.path("strengths"), "STRENGTH", "STRENGTH", "LOW",
                    "LEGACY_REVIEW", "历史评审将其识别为论文优点");
            if (findings.isEmpty()) throw new IllegalArgumentException("V1 结果只有分数，不能直接投影");
            return snapshot(review, review, LEGACY_PROJECTION_VERSION, findings);
        } catch (Exception exception) {
            throw new IllegalArgumentException("BASIC_REVIEW_V1 结果无法形成确定性投影", exception);
        }
    }

    private void append(List<ReviewEvidenceSnapshot.Finding> target, JsonNode array,
                        String idPart, String type, String severity, String category,
                        String impact) {
        if (!array.isArray()) return;
        int index = 0;
        for (JsonNode value : array) {
            index++;
            if (!value.isTextual() || value.asText().isBlank()) continue;
            target.add(new ReviewEvidenceSnapshot.Finding(
                    "LEGACY-" + idPart + "-" + index, type, category, severity,
                    value.asText(), impact, "$." + arrayName(idPart) + "[" + (index - 1) + "]",
                    List.of()));
        }
    }

    private String arrayName(String idPart) {
        return switch (idPart) {
            case "WEAKNESS" -> "weaknesses";
            case "SUGGESTION" -> "suggestions";
            default -> "strengths";
        };
    }

    private ReviewEvidenceSnapshot snapshot(ReviewSummaryDTO eligibility,
                                            ReviewSummaryDTO evidenceReview,
                                            String projectionVersion,
                                            List<ReviewEvidenceSnapshot.Finding> findings) throws Exception {
        Map<String, Object> json = new java.util.LinkedHashMap<>();
        json.put("eligibilityReviewTaskId", eligibility.getTaskId());
        json.put("evidenceReviewTaskId", evidenceReview.getTaskId());
        json.put("reviewWorkflowVersion", evidenceReview.getWorkflowVersion());
        json.put("projectionVersion", projectionVersion);
        json.put("findings", findings);
        return new ReviewEvidenceSnapshot(eligibility.getTaskId(), evidenceReview.getTaskId(),
                evidenceReview.getWorkflowVersion(), projectionVersion, List.copyOf(findings),
                objectMapper.writeValueAsString(json));
    }

    private boolean nonEmpty(JsonNode value) {
        return value.isArray() && !value.isEmpty();
    }
}
