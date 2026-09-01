package com.leetmodel.ranking.service;

import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.logging.FailureLogLimiter;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
@ConditionalOnProperty(prefix = "ranking.reconciliation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RankingReconciliationService {
    private static final String FAILURE_KEY = "ranking.reconciliation";
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final RankingRebuildRequestService requestService;
    private final FailureLogLimiter failureLogLimiter;

    public RankingReconciliationService(SubmissionFeignClient submissionFeignClient,
                                        ReviewFeignClient reviewFeignClient,
                                        RankingRebuildRequestService requestService,
                                        ObjectProvider<FailureLogLimiter> failureLogLimiterProvider) {
        this(submissionFeignClient, reviewFeignClient, requestService,
                failureLogLimiterProvider.getIfAvailable(FailureLogLimiter::disabled));
    }

    RankingReconciliationService(SubmissionFeignClient submissionFeignClient,
                                 ReviewFeignClient reviewFeignClient,
                                 RankingRebuildRequestService requestService,
                                 FailureLogLimiter failureLogLimiter) {
        this.submissionFeignClient = submissionFeignClient;
        this.reviewFeignClient = reviewFeignClient;
        this.requestService = requestService;
        this.failureLogLimiter = failureLogLimiter;
    }

    RankingReconciliationService(SubmissionFeignClient submissionFeignClient,
                                 ReviewFeignClient reviewFeignClient,
                                 RankingRebuildRequestService requestService) {
        this(submissionFeignClient, reviewFeignClient, requestService,
                FailureLogLimiter.disabled());
    }

    @Scheduled(fixedDelayString = "${ranking.reconciliation.interval-ms:3600000}",
            initialDelayString = "${ranking.reconciliation.initial-delay-ms:60000}")
    public void reconcile() {
        try {
            Result<List<SubmissionSnapshotDTO>> submissionsResult =
                    submissionFeignClient.listFinalSubmissions(null);
            Result<List<ReviewSummaryDTO>> reviewsResult = reviewFeignClient.listCompleted(null);
            if (!valid(submissionsResult) || !valid(reviewsResult)) {
                logFailure("INVALID_RESPONSE", null);
                return;
            }
            reconcile(submissionsResult.getData(), reviewsResult.getData());
            logRecovery();
        } catch (RuntimeException exception) {
            logFailure("CALL_FAILED", exception);
        }
    }

    private void logFailure(String category, RuntimeException exception) {
        FailureLogLimiter.Decision decision = failureLogLimiter.onFailure(
                FAILURE_KEY, LogEventCodes.DEPENDENCY_CALL_FAILED);
        if (!decision.shouldLog()) return;
        var event = log.atWarn()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DEPENDENCY_CALL_FAILED)
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, category)
                .addKeyValue(LogFieldNames.SUPPRESSED_COUNT, decision.suppressedCount());
        if (exception != null) {
            event.addKeyValue(LogFieldNames.EXCEPTION_TYPE, exception.getClass().getName());
        }
        event.log(decision.kind() == FailureLogLimiter.Kind.SUMMARY
                ? "Ranking reconciliation dependency remains unavailable"
                : "Ranking reconciliation dependency unavailable");
    }

    private void logRecovery() {
        FailureLogLimiter.Decision decision = failureLogLimiter.onRecovery(FAILURE_KEY);
        if (!decision.shouldLog()) return;
        log.atInfo()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DEPENDENCY_CALL_RECOVERED)
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, "RANKING_RECONCILIATION")
                .addKeyValue(LogFieldNames.SUPPRESSED_COUNT, decision.suppressedCount())
                .log("Ranking reconciliation dependency recovered");
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
