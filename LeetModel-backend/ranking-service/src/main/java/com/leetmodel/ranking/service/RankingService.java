package com.leetmodel.ranking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.common.cache.CacheSpec;
import com.leetmodel.common.cache.MultiLevelCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.ranking.cache.RankingCachePolicy;
import com.leetmodel.ranking.entity.RankingSnapshot;
import com.leetmodel.ranking.enums.RankingErrorCode;
import com.leetmodel.ranking.mapper.RankingSnapshotMapper;
import com.leetmodel.ranking.vo.RankingEntryVO;
import com.leetmodel.ranking.vo.RankingOverviewVO;
import com.leetmodel.ranking.vo.TeamRankingContextVO;
import com.leetmodel.ranking.vo.GlobalRankingOverviewVO;
import com.leetmodel.ranking.vo.ProblemRankingStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import java.time.Duration;

/**
 * 负责从最终提交和已完成评审构建可追溯的当前排行。
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    private static final int CURRENT = 1;

    private final RankingSnapshotMapper snapshotMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final ProblemFeignClient problemFeignClient;
    private final MultiLevelCache cache;
    private final CacheInvalidator cacheInvalidator;
    private final ObjectMapper objectMapper;

    /**
     * 重建指定题目的当前排行。所有依赖数据先完整读取，失败时不会覆盖已有排行。
     *
     * @param problemId 题目 ID
     * @return 本次构建的排行结果
     */
    @Transactional
    public RankingOverviewVO rebuild(Long problemId) {
        List<SubmissionSnapshotDTO> submissions = requiredData(
                () -> submissionFeignClient.listFinalSubmissions(problemId));
        List<ReviewSummaryDTO> reviews = requiredData(
                () -> reviewFeignClient.listCompleted(problemId));

        Map<Long, SubmissionSnapshotDTO> finalByTeam = selectFinalSubmissions(problemId, submissions);
        Map<Long, ReviewSummaryDTO> reviewBySubmission = selectLatestReviews(problemId, reviews);
        List<RankingDraft> drafts = buildDrafts(problemId, finalByTeam, reviewBySubmission);
        drafts.sort(Comparator.comparing(RankingDraft::score).reversed()
                .thenComparing(RankingDraft::submittedAt)
                .thenComparing(RankingDraft::teamId));

        String batchId = UUID.randomUUID().toString();
        LocalDateTime computedAt = LocalDateTime.now();
        snapshotMapper.deactivateCurrent(problemId);

        BigDecimal previousScore = null;
        int rank = 0;
        List<RankingSnapshot> saved = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            RankingDraft draft = drafts.get(index);
            if (previousScore == null || previousScore.compareTo(draft.score()) != 0) {
                rank = index + 1;
                previousScore = draft.score();
            }
            RankingSnapshot snapshot = toSnapshot(problemId, batchId, computedAt, rank, draft);
            snapshotMapper.insert(snapshot);
            saved.add(snapshot);
        }
        cacheInvalidator.record(
                RankingCachePolicy.REGION,
                RankingCachePolicy.scope(problemId),
                RankingCachePolicy.SCHEMA_VERSION
        );
        return toOverview(problemId, saved);
    }

    /**
     * 查询指定题目的当前排行，可按队伍名称即时过滤。
     *
     * @param problemId 题目 ID
     * @param keyword 可选队伍名称关键字
     * @return 当前排行；尚未构建时返回空列表
     */
    public RankingOverviewVO getCurrent(Long problemId, String keyword) {
        RankingOverviewVO current = currentOverview(problemId);
        List<RankingEntryVO> entries = current.getItems();
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim().toLowerCase(Locale.ROOT);
            entries = entries.stream()
                    .filter(item -> item.getTeamName().toLowerCase(Locale.ROOT).contains(normalized))
                    .toList();
        }
        return RankingOverviewVO.builder()
                .problemId(current.getProblemId())
                .batchId(current.getBatchId())
                .computedAt(current.getComputedAt())
                .total(entries.size())
                .items(entries)
                .build();
    }

    /**
     * 定位一个队伍并返回上下文名次。
     *
     * @param problemId 题目 ID
     * @param teamId 队伍 ID
     * @param radius 上下各展示多少行，范围 0 到 10
     * @return 当前队伍与附近排行
     */
    public TeamRankingContextVO locate(Long problemId, Long teamId, int radius) {
        List<RankingEntryVO> entries = currentOverview(problemId).getItems();
        int currentIndex = -1;
        for (int index = 0; index < entries.size(); index++) {
            if (Objects.equals(entries.get(index).getTeamId(), teamId)) {
                currentIndex = index;
                break;
            }
        }
        BusinessException.throwIf(currentIndex < 0, RankingErrorCode.TEAM_RANKING_NOT_FOUND);
        int safeRadius = Math.max(0, Math.min(radius, 10));
        int from = Math.max(0, currentIndex - safeRadius);
        int to = Math.min(entries.size(), currentIndex + safeRadius + 1);
        return TeamRankingContextVO.builder()
                .current(entries.get(currentIndex))
                .nearby(entries.subList(from, to))
                .total(entries.size())
                .build();
    }

    /**
     * 获取当前排行记录数。
     *
     * @return 当前记录数
     */
    public long countCurrent() {
        return snapshotMapper.selectCount(new LambdaQueryWrapper<RankingSnapshot>()
                .eq(RankingSnapshot::getCurrentMarker, CURRENT));
    }

    /**
     * 聚合所有题目的成功提交量、已完成评审分数与当前上榜队伍数。
     * 数据来自各 owner 服务的全量事实，不使用管理端最近 N 条快照。
     */
    public GlobalRankingOverviewVO getGlobalStats() {
        List<ProblemSubmissionStatsDTO> submissionStats = requiredData(
                submissionFeignClient::getProblemSubmissionStats);
        List<ReviewSummaryDTO> completedReviews = requiredData(
                () -> reviewFeignClient.listCompleted(null));

        Map<Long, ReviewSummaryDTO> latestReviewBySubmission = new HashMap<>();
        for (ReviewSummaryDTO review : completedReviews) {
            if (review == null || review.getSubmissionId() == null || review.getProblemId() == null
                    || review.getScore() == null || !"COMPLETED".equals(review.getStatus())) continue;
            latestReviewBySubmission.merge(review.getSubmissionId(), review, (left, right) -> {
                LocalDateTime leftTime = left.getFinishedAt();
                LocalDateTime rightTime = right.getFinishedAt();
                if (leftTime == null) return right;
                if (rightTime == null) return left;
                return rightTime.isAfter(leftTime) ? right : left;
            });
        }

        Map<Long, ScoreAggregate> scoreByProblem = new HashMap<>();
        for (ReviewSummaryDTO review : latestReviewBySubmission.values()) {
            scoreByProblem.computeIfAbsent(review.getProblemId(), ignored -> new ScoreAggregate())
                    .add(review.getScore());
        }

        List<RankingSnapshot> currentSnapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<RankingSnapshot>()
                        .eq(RankingSnapshot::getCurrentMarker, CURRENT));
        Map<Long, Long> rankedTeamsByProblem = currentSnapshots.stream()
                .collect(Collectors.groupingBy(RankingSnapshot::getProblemId, Collectors.counting()));

        Map<Long, Long> submissionsByProblem = submissionStats.stream()
                .filter(item -> item.getProblemId() != null)
                .collect(Collectors.toMap(ProblemSubmissionStatsDTO::getProblemId,
                        item -> item.getSubmissionCount() == null ? 0L : item.getSubmissionCount(),
                        Long::sum));
        Set<Long> problemIds = new HashSet<>(submissionsByProblem.keySet());
        problemIds.addAll(scoreByProblem.keySet());
        problemIds.addAll(rankedTeamsByProblem.keySet());

        Map<Long, ProblemPracticeDTO> problemById = problemIds.isEmpty() ? Map.of() : requiredData(
                () -> problemFeignClient.getPracticeProblems(problemIds.stream().sorted().toList()))
                .stream().filter(problem -> problem.getId() != null)
                .collect(Collectors.toMap(ProblemPracticeDTO::getId, problem -> problem));

        List<ProblemRankingStatsVO> items = problemIds.stream().map(problemId -> {
            ProblemPracticeDTO problem = problemById.get(problemId);
            ScoreAggregate scores = scoreByProblem.get(problemId);
            return ProblemRankingStatsVO.builder()
                    .problemId(problemId)
                    .problemCode(problem == null ? null : problem.getCode())
                    .problemTitle(problem == null ? "题目 " + problemId : problem.getTitle())
                    .submissionCount(submissionsByProblem.getOrDefault(problemId, 0L))
                    .reviewedSubmissionCount(scores == null ? 0L : scores.count)
                    .rankedTeamCount(rankedTeamsByProblem.getOrDefault(problemId, 0L))
                    .averageScore(scores == null ? null : scores.average())
                    .highestScore(scores == null ? null : scores.highest)
                    .build();
        }).sorted(Comparator.comparing(ProblemRankingStatsVO::getSubmissionCount).reversed()
                .thenComparing(ProblemRankingStatsVO::getAverageScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        ScoreAggregate overall = new ScoreAggregate();
        latestReviewBySubmission.values().forEach(review -> overall.add(review.getScore()));
        return GlobalRankingOverviewVO.builder()
                .totalSubmissions(submissionsByProblem.values().stream().mapToLong(Long::longValue).sum())
                .reviewedSubmissions(overall.count)
                .rankedTeams(currentSnapshots.stream().map(RankingSnapshot::getTeamId).distinct().count())
                .problemCount(items.size())
                .overallAverageScore(overall.count == 0 ? null : overall.average())
                .computedAt(LocalDateTime.now())
                .items(items)
                .build();
    }

    private List<RankingDraft> buildDrafts(Long problemId,
                                            Map<Long, SubmissionSnapshotDTO> finalByTeam,
                                            Map<Long, ReviewSummaryDTO> reviewBySubmission) {
        List<RankingDraft> drafts = new ArrayList<>();
        for (SubmissionSnapshotDTO submission : finalByTeam.values()) {
            ReviewSummaryDTO review = reviewBySubmission.get(submission.getId());
            if (review == null) {
                continue;
            }
            BusinessException.throwIf(!Objects.equals(submission.getTeamId(), review.getTeamId())
                            || !Objects.equals(problemId, review.getProblemId()),
                    RankingErrorCode.SOURCE_DATA_INVALID);
            TeamDTO team = requiredData(() -> teamFeignClient.getTeamInfo(submission.getTeamId()));
            BusinessException.throwIf(team.getId() == null
                            || !Objects.equals(team.getId(), submission.getTeamId())
                            || (team.getProblemId() != null && !Objects.equals(team.getProblemId(), problemId))
                            || team.getName() == null || team.getName().isBlank(),
                    RankingErrorCode.SOURCE_DATA_INVALID);
            drafts.add(new RankingDraft(
                    submission.getTeamId(), team.getName(), submission.getId(),
                    review.getTaskId(), review.getWorkflowVersion(), review.getScore(),
                    submission.getCreateTime(), review.getFinishedAt()));
        }
        return drafts;
    }

    private Map<Long, SubmissionSnapshotDTO> selectFinalSubmissions(
            Long problemId, List<SubmissionSnapshotDTO> submissions) {
        Map<Long, SubmissionSnapshotDTO> selected = new LinkedHashMap<>();
        Comparator<SubmissionSnapshotDTO> newest = Comparator
                .comparing(SubmissionSnapshotDTO::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SubmissionSnapshotDTO::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder()));
        for (SubmissionSnapshotDTO submission : submissions) {
            if (submission == null || !Boolean.TRUE.equals(submission.getFinalVersion())
                    || !Objects.equals(problemId, submission.getProblemId())
                    || submission.getId() == null || submission.getTeamId() == null
                    || submission.getCreateTime() == null) {
                continue;
            }
            selected.merge(submission.getTeamId(), submission,
                    (left, right) -> newest.compare(left, right) >= 0 ? left : right);
        }
        return selected;
    }

    private Map<Long, ReviewSummaryDTO> selectLatestReviews(
            Long problemId, List<ReviewSummaryDTO> reviews) {
        Map<Long, ReviewSummaryDTO> selected = new HashMap<>();
        Comparator<ReviewSummaryDTO> newest = Comparator
                .comparing(ReviewSummaryDTO::getFinishedAt)
                .thenComparing(ReviewSummaryDTO::getTaskId);
        for (ReviewSummaryDTO review : reviews) {
            if (review == null || !"COMPLETED".equals(review.getStatus())
                    || !Objects.equals(problemId, review.getProblemId())
                    || review.getSubmissionId() == null || review.getTaskId() == null
                    || review.getScore() == null || review.getFinishedAt() == null
                    || review.getWorkflowVersion() == null || review.getWorkflowVersion().isBlank()) {
                continue;
            }
            selected.merge(review.getSubmissionId(), review,
                    (left, right) -> newest.compare(left, right) >= 0 ? left : right);
        }
        return selected;
    }

    private RankingSnapshot toSnapshot(Long problemId, String batchId, LocalDateTime computedAt,
                                       int rank, RankingDraft draft) {
        RankingSnapshot snapshot = new RankingSnapshot();
        snapshot.setBatchId(batchId);
        snapshot.setProblemId(problemId);
        snapshot.setTeamId(draft.teamId());
        snapshot.setTeamName(draft.teamName());
        snapshot.setSubmissionId(draft.submissionId());
        snapshot.setReviewTaskId(draft.reviewTaskId());
        snapshot.setWorkflowVersion(draft.workflowVersion());
        snapshot.setScore(draft.score());
        snapshot.setRankNo(rank);
        snapshot.setSubmittedAt(draft.submittedAt());
        snapshot.setReviewFinishedAt(draft.reviewFinishedAt());
        snapshot.setComputedAt(computedAt);
        snapshot.setCurrentMarker(CURRENT);
        return snapshot;
    }

    private List<RankingSnapshot> listCurrent(Long problemId) {
        return snapshotMapper.selectList(new LambdaQueryWrapper<RankingSnapshot>()
                .eq(RankingSnapshot::getProblemId, problemId)
                .eq(RankingSnapshot::getCurrentMarker, CURRENT)
                .orderByAsc(RankingSnapshot::getRankNo)
                .orderByDesc(RankingSnapshot::getScore)
                .orderByAsc(RankingSnapshot::getSubmittedAt)
                .orderByAsc(RankingSnapshot::getTeamId));
    }

    /**
     * 通过 Caffeine 和 Redis 读取整份当前排行读模型。
     *
     * @param problemId 题目 ID
     * @return 未经关键词过滤的当前排行
     */
    private RankingOverviewVO currentOverview(Long problemId) {
        CacheSpec spec = new CacheSpec(
                RankingCachePolicy.REGION,
                RankingCachePolicy.scope(problemId),
                RankingCachePolicy.SCHEMA_VERSION,
                "overview",
                Duration.ofSeconds(15),
                Duration.ofMinutes(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );
        return cache.get(
                spec,
                objectMapper.constructType(RankingOverviewVO.class),
                () -> toOverview(problemId, listCurrent(problemId))
        );
    }

    private RankingOverviewVO toOverview(Long problemId, List<RankingSnapshot> snapshots) {
        RankingSnapshot first = snapshots.isEmpty() ? null : snapshots.get(0);
        return RankingOverviewVO.builder()
                .problemId(problemId)
                .batchId(first == null ? null : first.getBatchId())
                .computedAt(first == null ? null : first.getComputedAt())
                .total(snapshots.size())
                .items(snapshots.stream().map(this::toEntry).toList())
                .build();
    }

    private RankingEntryVO toEntry(RankingSnapshot snapshot) {
        return RankingEntryVO.builder()
                .rank(snapshot.getRankNo())
                .teamId(snapshot.getTeamId())
                .teamName(snapshot.getTeamName())
                .submissionId(snapshot.getSubmissionId())
                .score(snapshot.getScore())
                .workflowVersion(snapshot.getWorkflowVersion())
                .submittedAt(snapshot.getSubmittedAt())
                .reviewFinishedAt(snapshot.getReviewFinishedAt())
                .build();
    }

    private <T> T requiredData(Supplier<Result<T>> call) {
        try {
            Result<T> result = call.get();
            BusinessException.throwIf(result == null || !result.isSuccess() || result.getData() == null,
                    RankingErrorCode.DEPENDENCY_UNAVAILABLE);
            return result.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(RankingErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private record RankingDraft(Long teamId, String teamName, Long submissionId,
                                Long reviewTaskId, String workflowVersion, BigDecimal score,
                                LocalDateTime submittedAt, LocalDateTime reviewFinishedAt) {
    }

    private static final class ScoreAggregate {
        private long count;
        private BigDecimal total = BigDecimal.ZERO;
        private BigDecimal highest;

        private void add(BigDecimal score) {
            if (score == null) return;
            count++;
            total = total.add(score);
            if (highest == null || score.compareTo(highest) > 0) highest = score;
        }

        private BigDecimal average() {
            return count == 0 ? null : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
}
