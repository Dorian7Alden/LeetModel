package com.leetmodel.ranking.service;

import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingReconciliationServiceTest {
    @Mock SubmissionFeignClient submissionFeignClient;
    @Mock ReviewFeignClient reviewFeignClient;
    @Mock RankingRebuildRequestService requestService;

    @Test
    void sameFactsProduceStablePerProblemFingerprintRegardlessOfInputOrder() {
        RankingReconciliationService service = new RankingReconciliationService(
                submissionFeignClient, reviewFeignClient, requestService);
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 8, 0);
        SubmissionSnapshotDTO submission = new SubmissionSnapshotDTO(
                31L, 41L, 51L, 1L, 1, "paper.pdf", "paper.pdf",
                "SUCCESS", true, now);
        ReviewSummaryDTO review = new ReviewSummaryDTO(
                21L, 31L, 41L, 51L, "COMPLETED", "EVIDENCE_REVIEW_V2",
                new BigDecimal("88"), "{}", "model", "call", null, now.plusMinutes(1));

        service.reconcile(List.of(submission), List.of(review));

        var fingerprint = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(requestService).requestIfFingerprintChanged(eq(51L), fingerprint.capture(), anyString());
        assertThat(fingerprint.getValue()).hasSize(64);
    }
}
