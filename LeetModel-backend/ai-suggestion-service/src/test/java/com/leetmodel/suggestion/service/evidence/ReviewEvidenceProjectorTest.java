package com.leetmodel.suggestion.service.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewEvidenceProjectorTest {
    private final ReviewEvidenceProjector projector = new ReviewEvidenceProjector(new ObjectMapper());

    @Test
    void projectsStructuredV1FeedbackWithStableIdsAndSourcePaths() {
        ReviewSummaryDTO review = review("BASIC_REVIEW_V1",
                "{\"score\":80,\"weaknesses\":[\"缺少敏感性分析\"],\"suggestions\":[\"补充参数扰动\"]}");

        ReviewEvidenceSnapshot snapshot = projector.projectLegacy(review);

        assertThat(snapshot.projectionVersion()).isEqualTo("LEGACY_REVIEW_EVIDENCE_V1");
        assertThat(snapshot.findings()).extracting(ReviewEvidenceSnapshot.Finding::findingId)
                .containsExactly("LEGACY-WEAKNESS-1", "LEGACY-SUGGESTION-1");
        assertThat(snapshot.findings().get(0).sourcePath()).contains("weaknesses");
    }

    @Test
    void refusesToPretendScoreOnlyV1HasEvidence() {
        ReviewSummaryDTO review = review("BASIC_REVIEW_V1", "{\"score\":80}");

        assertThat(projector.hasStructuredLegacyEvidence(review)).isFalse();
        assertThatThrownBy(() -> projector.projectLegacy(review))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dimensionScoresWithoutActionableFeedbackRequireANewEvidenceReview() {
        ReviewSummaryDTO review = review("BASIC_REVIEW_V1",
                "{\"score\":80,\"dimensions\":{\"modelCreativity\":{\"score\":80,\"comment\":\"一般\"}}}");

        assertThat(projector.hasStructuredLegacyEvidence(review)).isFalse();
    }

    @Test
    void mapsNativeEvidenceIdsBackToPaperBlocks() {
        String json = "{\"findings\":[{\"findingId\":\"F-1\",\"type\":\"ISSUE\","
                + "\"category\":\"VALIDATION\",\"severity\":\"HIGH\","
                + "\"statement\":\"未验证\",\"scoreImpact\":\"可信度不足\",\"evidenceIds\":[\"EV-1\"]}],"
                + "\"evidence\":[{\"evidenceId\":\"EV-1\",\"blockId\":\"P2-B1\"}]}";
        ReviewSummaryDTO review = review("EVIDENCE_REVIEW_V2", json);

        ReviewEvidenceSnapshot snapshot = projector.nativeV2(review, review);

        assertThat(snapshot.findings().get(0).paperEvidenceIds()).containsExactly("P2-B1");
        assertThat(snapshot.projectionVersion()).isNull();
    }

    @Test
    void mapsNativeV3EvidenceDirectlyWithBlockId() {
        String json = "{\"findings\":[{\"findingId\":\"F_Q1_001\",\"type\":\"ISSUE\","
                + "\"dimensionCode\":\"FORMULATION\",\"severity\":\"HIGH\","
                + "\"statement\":\"目标函数遗漏非线性约束\",\"scoreImpact\":\"-2.0 分\",\"blockId\":\"P5-B3\"}]}";
        ReviewSummaryDTO review = review("DEEP_EVIDENCE_REVIEW_V3", json);

        assertThat(projector.isNativeV3(review)).isTrue();
        ReviewEvidenceSnapshot snapshot = projector.nativeV3(review, review);
        assertThat(snapshot.findings().get(0).findingId()).isEqualTo("F_Q1_001");
        assertThat(snapshot.findings().get(0).paperEvidenceIds()).containsExactly("P5-B3");
    }

    private ReviewSummaryDTO review(String workflow, String json) {
        return new ReviewSummaryDTO(9L, 101L, 11L, 51L, "COMPLETED", workflow,
                BigDecimal.valueOf(80), json, "model", "call", null, null);
    }
}
