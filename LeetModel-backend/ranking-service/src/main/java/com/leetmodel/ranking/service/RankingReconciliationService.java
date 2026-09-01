package com.leetmodel.ranking.service;

import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** 每小时比较权威事实指纹，补建可能遗漏的排行事件。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ranking.reconciliation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RankingReconciliationService {
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final RankingRebuildRequestService requestService;

    @Scheduled(fixedDelayString = "${ranking.reconciliation.interval-ms:3600000}",
            initialDelayString = "${ranking.reconciliation.initial-delay-ms:60000}")
    public void reconcile() {
        try {
            Result<List<SubmissionSnapshotDTO>> submissionsResult =
                    submissionFeignClient.listFinalSubmissions(null);
            Result<List<ReviewSummaryDTO>> reviewsResult = reviewFeignClient.listCompleted(null);
            if (!valid(submissionsResult) || !valid(reviewsResult)) {
                log.warn("排行事件对账依赖暂不可用，本轮不推进指纹");
                return;
            }
            reconcile(submissionsResult.getData(), reviewsResult.getData());
        } catch (RuntimeException exception) {
            log.warn("排行事件对账失败，本轮不推进指纹: {}", exception.getMessage());
        }
    }

    void reconcile(List<SubmissionSnapshotDTO> submissions, List<ReviewSummaryDTO> reviews) {
        Map<Long, List<String>> facts = new HashMap<>();
        for (SubmissionSnapshotDTO submission : submissions) {
            if (submission == null || !Boolean.TRUE.equals(submission.getFinalVersion())
                    || submission.getProblemId() == null || submission.getId() == null) continue;
            facts.computeIfAbsent(submission.getProblemId(), ignored -> new ArrayList<>()).add(
                    "S:" + submission.getId() + ":" + submission.getTeamId()
                            + ":" + submission.getCreateTime());
        }
        for (ReviewSummaryDTO review : reviews) {
            if (review == null || !"COMPLETED".equals(review.getStatus())
                    || review.getProblemId() == null || review.getTaskId() == null) continue;
            facts.computeIfAbsent(review.getProblemId(), ignored -> new ArrayList<>()).add(
                    "R:" + review.getTaskId() + ":" + review.getSubmissionId()
                            + ":" + review.getFinishedAt());
        }
        Set<Long> problemIds = new TreeSet<>(facts.keySet());
        for (Long problemId : problemIds) {
            List<String> tokens = facts.get(problemId);
            tokens.sort(String::compareTo);
            requestService.requestIfFingerprintChanged(
                    problemId, fingerprint(tokens), "ranking-reconcile:" + UUID.randomUUID());
        }
    }

    private boolean valid(Result<?> result) {
        return result != null && result.isSuccess() && result.getData() != null;
    }

    private String fingerprint(List<String> tokens) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    String.join("\n", tokens).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }
}
