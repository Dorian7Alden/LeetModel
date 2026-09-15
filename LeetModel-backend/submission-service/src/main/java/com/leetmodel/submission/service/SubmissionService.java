package com.leetmodel.submission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.common.api.dto.AdminSubmissionPageQuery;
import com.leetmodel.common.api.dto.AdminSubmissionStatsDTO;
import com.leetmodel.common.api.dto.FinalSubmissionChangedPayload;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.api.vo.SubmissionAdminVO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionLock;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.messaging.FinalSubmissionMessageContract;
import com.leetmodel.submission.vo.SubmissionVO;
import com.leetmodel.submission.vo.ProblemSubmissionStatsVO;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionMapper submissionMapper;
    private final SubmissionLockMapper lockMapper;
    private final TeamFeignClient teamFeignClient;
    private final ProblemFeignClient problemFeignClient;
    private final UserFeignClient userFeignClient;
    private final StorageService storageService;
    private final ReviewDispatchQueryService reviewDispatchQueryService;
    private final SubmissionFinalizationPersistenceService finalizationPersistenceService;
    private final SubmissionUploadPersistenceService uploadPersistenceService;
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageOutbox messageOutbox;

    /**
     * 查询指定队伍的提交历史记录（倒序排列）。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @param userId 操作用户 ID，不能为 null
     * @return 队伍提交记录视图列表
     * @throws BusinessException 若队伍不存在或用户非队员
     */
    public List<SubmissionVO> history(Long teamId, Long userId) {
        requiredMemberTeam(teamId, userId);
        SubmissionLock lock = lockMapper.selectOne(new LambdaQueryWrapper<SubmissionLock>()
                .eq(SubmissionLock::getTeamId, teamId));
        Long finalSubmissionId = lock == null ? null : lock.getSubmissionId();
        List<Submission> submissions = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTeamId, teamId).orderByDesc(Submission::getVersion));
        Map<Long, Integer> codeByProblem = loadProblemCodes(submissions.stream()
                .map(Submission::getProblemId).distinct().toList());
        return submissions.stream().map(value -> {
            SubmissionVO vo = toVO(value, finalSubmissionId);
            vo.setProblemCode(codeByProblem.get(value.getProblemId()));
            return vo;
        }).toList();
    }

    /**
     * 批量查询题目题号（短顺序编号），用于提交记录展示。
     */
    private Map<Long, Integer> loadProblemCodes(List<Long> problemIds) {
        if (problemIds.isEmpty()) return Map.of();
        Result<List<ProblemPracticeDTO>> result = problemFeignClient.getPracticeProblems(problemIds);
        if (result == null || !result.isSuccess() || result.getData() == null) return Map.of();
        return result.getData().stream()
                .filter(problem -> problem.getCode() != null)
                .collect(Collectors.toMap(ProblemPracticeDTO::getId, ProblemPracticeDTO::getCode));
    }

    /**
     * 锁定队伍的最终作品提交版本（触发榜单与评审异步事件）。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @param userId 操作用户 ID，不能为 null
     * @return 最终锁定的提交版本视图对象
     * @throws BusinessException 若未达截止时间或没有成功提交记录
     */
    @Transactional
    public SubmissionVO lockFinal(Long teamId, Long userId) {
        TeamDTO team = requiredMemberTeam(teamId, userId);
        BusinessException.throwIf(!"ENDED".equals(team.getPracticeStatus())
                        && (team.getDeadlineAt() == null || LocalDateTime.now().isBefore(team.getDeadlineAt())),
                SubmissionErrorCode.DEADLINE_NOT_REACHED);
        Submission finalSubmission = finalizationPersistenceService.lockFinal(team);
        return toVO(finalSubmission, finalSubmission.getId());
    }

    /**
     * 定时自动扫描截止时间已到达的实训队伍并执行最终版本锁定。
     */
    @Scheduled(fixedDelayString = "${submission.finalizer.delay-ms:60000}")
    public void finalizeExpiredPractices() {
        Result<List<TeamDTO>> response = teamFeignClient.listExpiredPractices();
        if (response == null || !response.isSuccess() || response.getData() == null) return;
        for (TeamDTO team : response.getData()) {
            try {
                finalizationPersistenceService.lockFinal(team);
            } catch (BusinessException ignored) {
                // 没有成功提交的队伍保持原状态，等待人工处理。
            }
        }
    }

    /**
     * 查询指定提交记录的评审数据（供评审服务拉取）。
     *
     * @param id 目标提交 ID，不能为 null
     * @return 评审提交摘要 DTO
     * @throws BusinessException 若提交记录不存在
     */
    public SubmissionReviewDTO getForReview(Long id) {
        Submission value = requiredSubmission(id);
        return new SubmissionReviewDTO(value.getId(), value.getTeamId(), value.getProblemId(),
                value.getVersion(), value.getObjectName());
    }

    /**
     * 查询已经锁定的最终提交快照。
     * @param problemId 可选题目 ID
     * @return 最终提交快照
     */
    public List<SubmissionSnapshotDTO> listFinalSnapshots(Long problemId) {
        // 最终版本事实只来自 submission_lock
        List<SubmissionLock> locks = lockMapper.selectList(null);
        if (locks.isEmpty()) return List.of();

        Set<Long> finalIds = new HashSet<>();
        for (SubmissionLock lock : locks) finalIds.add(lock.getSubmissionId());
        List<Submission> submissions = submissionMapper.selectBatchIds(finalIds);

        // 可选按题目过滤，并按提交时间倒序输出
        return submissions.stream()
                .filter(value -> problemId == null || problemId.equals(value.getProblemId()))
                .sorted(Comparator.comparing(
                        Submission::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toSnapshot)
                .toList();
    }

    /**
     * 获取提交记录数量。
     * @return 提交数量
     */
    public long count() {
        return submissionMapper.selectCount(null);
    }

    /** 按题目统计全部成功提交，不受管理端列表条数限制。 */
    public List<ProblemSubmissionStatsDTO> listProblemStats() {
        return submissionMapper.selectProblemStats();
    }

    /**
     * 查询指定题目的成功提交总次数。
     *
     * @param problemId 题目标识
     * @return 成功提交统计
     */
    public ProblemSubmissionStatsVO getProblemSubmissionStats(Long problemId) {
        return ProblemSubmissionStatsVO.builder()
                .problemId(problemId)
                .submissionCount(submissionMapper.countSuccessfulByProblemId(problemId))
                .build();
    }

    /** 管理聚合使用的最近提交快照，不暴露下载地址。 */
    public List<SubmissionSnapshotDTO> listRecentSnapshots(int limit) {
        Set<Long> finalIds = lockMapper.selectList(null).stream()
                .map(SubmissionLock::getSubmissionId).collect(java.util.stream.Collectors.toSet());
        return submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                        .orderByDesc(Submission::getCreateTime).last("LIMIT " + limit))
                .stream().map(value -> {
                    SubmissionSnapshotDTO snapshot = toSnapshot(value);
                    snapshot.setFinalVersion(finalIds.contains(value.getId()));
                    return snapshot;
                }).toList();
    }

    /** 按提交生成临时 PDF 预览地址，不把地址固化到列表快照。 */
    public SubmissionPreviewDTO getPreview(Long submissionId) {
        Submission submission = requiredSubmission(submissionId);
        return new SubmissionPreviewDTO(submission.getId(), submission.getOriginalFilename(),
                storageService.getUrl(submission.getObjectName()));
    }

    /**
     * 返回事务 Outbox 的评审派发状态；请求线程不直接调用评审服务。
     * @param submission 提交记录
     * @return 提交响应
     */
    public SubmissionVO triggerReview(Submission submission) {
        return toVO(submission);
    }

    /**
     * 按 ID 获取提交记录。
     * @param submissionId 提交 ID
     * @return 提交记录
     */
    public Submission getSubmission(Long submissionId) {
        return requiredSubmission(submissionId);
    }

    private TeamDTO requiredMemberTeam(Long teamId, Long userId) {
        Result<TeamDTO> teamResult = teamFeignClient.getTeamInfo(teamId);
        Result<List<Long>> membersResult = teamFeignClient.getMemberIds(teamId);
        BusinessException.throwIf(teamResult == null || !teamResult.isSuccess() || teamResult.getData() == null,
                SubmissionErrorCode.TEAM_NOT_AVAILABLE);
        BusinessException.throwIf(membersResult == null || !membersResult.isSuccess()
                        || membersResult.getData() == null || !membersResult.getData().contains(userId),
                SubmissionErrorCode.NOT_TEAM_MEMBER);
        return teamResult.getData();
    }

    private Submission requiredSubmission(Long id) {
        Submission value = submissionMapper.selectById(id);
        BusinessException.throwIf(value == null, SubmissionErrorCode.SUBMISSION_NOT_FOUND);
        return value;
    }

    private SubmissionVO toVO(Submission value) {
        return toVO(value, null);
    }

    private SubmissionVO toVO(Submission value, Long finalSubmissionId) {
        return SubmissionVO.builder().id(value.getId()).teamId(value.getTeamId()).problemId(value.getProblemId())
                .submitterId(value.getSubmitterId()).version(value.getVersion())
                .originalFilename(value.getOriginalFilename()).fileSize(value.getFileSize()).status(value.getStatus())
                .reviewDispatchStatus(reviewDispatchQueryService.status(value.getId()))
                .finalVersion(value.getId().equals(finalSubmissionId))
                .downloadUrl(storageService.getUrl(value.getObjectName())).createTime(value.getCreateTime()).build();
    }

    /**
     * 转换最终提交快照。
     * @param value 提交实体
     * @return 最终提交快照
     */
    private SubmissionSnapshotDTO toSnapshot(Submission value) {
        return new SubmissionSnapshotDTO(
                value.getId(),
                value.getTeamId(),
                value.getProblemId(),
                value.getSubmitterId(),
                value.getVersion(),
                value.getOriginalFilename(),
                value.getObjectName(),
                value.getStatus(),
                true,
                value.getCreateTime()
        );
    }

    /** 管理端多维分页查询提交记录。 */
    public PageResult<SubmissionAdminVO> pageAdminSubmissions(AdminSubmissionPageQuery query) {
        Page<Submission> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();

        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> {
                w.like(Submission::getOriginalFilename, kw);
                try {
                    long idVal = Long.parseLong(kw);
                    w.or().eq(Submission::getId, idVal).or().eq(Submission::getTeamId, idVal);
                } catch (NumberFormatException ignored) {}
            });
        }
        if (query.getTeamId() != null) {
            wrapper.eq(Submission::getTeamId, query.getTeamId());
        }
        if (query.getProblemId() != null) {
            wrapper.eq(Submission::getProblemId, query.getProblemId());
        }
        if (query.getSubmitterId() != null) {
            wrapper.eq(Submission::getSubmitterId, query.getSubmitterId());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(Submission::getStatus, query.getStatus());
        }
        if (Boolean.TRUE.equals(query.getFinalOnly())) {
            wrapper.inSql(Submission::getId, "SELECT submission_id FROM submission_lock");
        } else if (Boolean.FALSE.equals(query.getFinalOnly())) {
            wrapper.notInSql(Submission::getId, "SELECT submission_id FROM submission_lock");
        }
        wrapper.orderByDesc(Submission::getCreateTime);

        IPage<Submission> subPage = submissionMapper.selectPage(page, wrapper);
        if (subPage.getRecords().isEmpty()) {
            return new PageResult<>(subPage.getTotal(), (int) subPage.getCurrent(), (int) subPage.getSize(), List.of());
        }

        List<Long> subIds = subPage.getRecords().stream().map(Submission::getId).toList();
        Set<Long> finalIds = lockMapper.selectList(new LambdaQueryWrapper<SubmissionLock>()
                .in(SubmissionLock::getSubmissionId, subIds))
                .stream().map(SubmissionLock::getSubmissionId).collect(Collectors.toSet());

        List<Long> teamIds = subPage.getRecords().stream().map(Submission::getTeamId).distinct().toList();
        Map<Long, String> teamNameMap = Map.of();
        try {
            Result<List<TeamDTO>> teamRes = teamFeignClient.listSummaries(teamIds);
            if (teamRes != null && teamRes.isSuccess() && teamRes.getData() != null) {
                teamNameMap = teamRes.getData().stream()
                        .filter(t -> t.getId() != null)
                        .collect(Collectors.toMap(TeamDTO::getId, TeamDTO::getName, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("获取队伍名称摘要失败: {}", e.getMessage());
        }

        List<Long> problemIds = subPage.getRecords().stream().map(Submission::getProblemId).distinct().toList();
        Map<Long, ProblemPracticeDTO> problemMap = Map.of();
        try {
            Result<List<ProblemPracticeDTO>> probRes = problemFeignClient.getPracticeProblems(problemIds);
            if (probRes != null && probRes.isSuccess() && probRes.getData() != null) {
                problemMap = probRes.getData().stream()
                        .filter(p -> p.getId() != null)
                        .collect(Collectors.toMap(ProblemPracticeDTO::getId, p -> p, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("获取题目摘要失败: {}", e.getMessage());
        }

        List<Long> submitterIds = subPage.getRecords().stream().map(Submission::getSubmitterId).distinct().toList();
        Map<Long, UserPublicSummaryDTO> submitterMap = Map.of();
        try {
            Result<List<UserPublicSummaryDTO>> userRes = userFeignClient.getPublicSummaries(submitterIds);
            if (userRes != null && userRes.isSuccess() && userRes.getData() != null) {
                submitterMap = userRes.getData().stream()
                        .filter(u -> u.getUserId() != null)
                        .collect(Collectors.toMap(UserPublicSummaryDTO::getUserId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("获取提交人摘要失败: {}", e.getMessage());
        }

        Map<Long, String> finalTeamMap = teamNameMap;
        Map<Long, ProblemPracticeDTO> finalProbMap = problemMap;
        Map<Long, UserPublicSummaryDTO> finalUserMap = submitterMap;
        List<SubmissionAdminVO> voList = subPage.getRecords().stream().map(sub -> {
            ProblemPracticeDTO prob = finalProbMap.get(sub.getProblemId());
            UserPublicSummaryDTO subUser = finalUserMap.get(sub.getSubmitterId());
            return SubmissionAdminVO.builder()
                    .id(sub.getId())
                    .teamId(sub.getTeamId())
                    .teamName(finalTeamMap.getOrDefault(sub.getTeamId(), "队伍 #" + sub.getTeamId()))
                    .problemId(sub.getProblemId())
                    .problemCode(prob != null ? prob.getCode() : null)
                    .problemTitle(prob != null ? prob.getTitle() : null)
                    .submitterId(sub.getSubmitterId())
                    .submitterName(subUser != null && subUser.getNickname() != null ? subUser.getNickname() : "用户 #" + sub.getSubmitterId())
                    .submitterAvatarUrl(subUser != null ? subUser.getAvatarUrl() : null)
                    .version(sub.getVersion())
                    .originalFilename(sub.getOriginalFilename())
                    .objectName(sub.getObjectName())
                    .fileSize(sub.getFileSize())
                    .status(sub.getStatus())
                    .finalVersion(finalIds.contains(sub.getId()))
                    .createTime(sub.getCreateTime())
                    .build();
        }).toList();

        return new PageResult<>(subPage.getTotal(), (int) subPage.getCurrent(), (int) subPage.getSize(), voList);
    }

    /** 管理端统计大盘指标。 */
    public AdminSubmissionStatsDTO getAdminSubmissionStats() {
        long total = submissionMapper.selectCount(null);
        long success = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, "SUCCESS"));
        long failed = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>().eq(Submission::getStatus, "FAILED"));
        long processing = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>().in(Submission::getStatus, "PROCESSING", "PENDING"));
        long finals = lockMapper.selectCount(null);

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long today = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>().ge(Submission::getCreateTime, todayStart));

        List<ProblemSubmissionStatsDTO> statsList = submissionMapper.selectProblemStats();
        List<Long> problemIds = statsList.stream().map(ProblemSubmissionStatsDTO::getProblemId).limit(5).toList();
        Map<Long, ProblemPracticeDTO> probMap = Map.of();
        try {
            if (!problemIds.isEmpty()) {
                Result<List<ProblemPracticeDTO>> probRes = problemFeignClient.getPracticeProblems(problemIds);
                if (probRes != null && probRes.isSuccess() && probRes.getData() != null) {
                    probMap = probRes.getData().stream().collect(Collectors.toMap(ProblemPracticeDTO::getId, p -> p, (a, b) -> a));
                }
            }
        } catch (Exception ignored) {}

        Map<Long, ProblemPracticeDTO> finalProbMap = probMap;
        List<AdminSubmissionStatsDTO.TopProblemSubmissionStats> topList = statsList.stream().limit(5).map(s -> {
            ProblemPracticeDTO prob = finalProbMap.get(s.getProblemId());
            return AdminSubmissionStatsDTO.TopProblemSubmissionStats.builder()
                    .problemId(s.getProblemId())
                    .problemCode(prob != null ? prob.getCode() : null)
                    .problemTitle(prob != null ? prob.getTitle() : "赛题 #" + s.getProblemId())
                    .submissionCount(s.getSubmissionCount())
                    .build();
        }).toList();

        return AdminSubmissionStatsDTO.builder()
                .totalSubmissions(total)
                .successSubmissions(success)
                .failedSubmissions(failed)
                .processingSubmissions(processing)
                .finalSubmissions(finals)
                .todaySubmissions(today)
                .topProblems(topList)
                .build();
    }

    /** 管理端获取单条提交详细档案。 */
    public SubmissionAdminVO getAdminSubmissionDetail(Long submissionId) {
        Submission sub = requiredSubmission(submissionId);
        boolean isFinal = lockMapper.selectCount(new LambdaQueryWrapper<SubmissionLock>()
                .eq(SubmissionLock::getSubmissionId, submissionId)) > 0;

        String teamName = null;
        try {
            Result<TeamDTO> teamRes = teamFeignClient.getTeamInfo(sub.getTeamId());
            if (teamRes != null && teamRes.isSuccess() && teamRes.getData() != null) {
                teamName = teamRes.getData().getName();
            }
        } catch (Exception ignored) {}

        ProblemPracticeDTO prob = null;
        try {
            Result<List<ProblemPracticeDTO>> probRes = problemFeignClient.getPracticeProblems(List.of(sub.getProblemId()));
            if (probRes != null && probRes.isSuccess() && probRes.getData() != null && !probRes.getData().isEmpty()) {
                prob = probRes.getData().get(0);
            }
        } catch (Exception ignored) {}

        String submitterName = null;
        String submitterAvatarUrl = null;
        try {
            Result<List<UserPublicSummaryDTO>> userRes = userFeignClient.getPublicSummaries(List.of(sub.getSubmitterId()));
            if (userRes != null && userRes.isSuccess() && userRes.getData() != null && !userRes.getData().isEmpty()) {
                submitterName = userRes.getData().get(0).getNickname();
                submitterAvatarUrl = userRes.getData().get(0).getAvatarUrl();
            }
        } catch (Exception ignored) {}

        return SubmissionAdminVO.builder()
                .id(sub.getId())
                .teamId(sub.getTeamId())
                .teamName(teamName != null ? teamName : "队伍 #" + sub.getTeamId())
                .problemId(sub.getProblemId())
                .problemCode(prob != null ? prob.getCode() : null)
                .problemTitle(prob != null ? prob.getTitle() : null)
                .submitterId(sub.getSubmitterId())
                .submitterName(submitterName != null ? submitterName : "用户 #" + sub.getSubmitterId())
                .submitterAvatarUrl(submitterAvatarUrl)
                .version(sub.getVersion())
                .originalFilename(sub.getOriginalFilename())
                .objectName(sub.getObjectName())
                .fileSize(sub.getFileSize())
                .status(sub.getStatus())
                .finalVersion(isFinal)
                .createTime(sub.getCreateTime())
                .build();
    }

    /** 管理端将指定提交设置为队伍的最终版本。 */
    @Transactional
    public SubmissionAdminVO setFinalVersion(Long submissionId) {
        Submission sub = requiredSubmission(submissionId);
        BusinessException.throwIf(!"SUCCESS".equals(sub.getStatus()), SubmissionErrorCode.SUBMISSION_NOT_FOUND);

        lockMapper.delete(new LambdaQueryWrapper<SubmissionLock>().eq(SubmissionLock::getTeamId, sub.getTeamId()));
        SubmissionLock lock = new SubmissionLock();
        lock.setTeamId(sub.getTeamId());
        lock.setSubmissionId(sub.getId());
        lock.setLockedAt(LocalDateTime.now());
        lockMapper.insert(lock);

        FinalSubmissionChangedPayload payload = new FinalSubmissionChangedPayload(
                sub.getTeamId(), sub.getProblemId(), sub.getId(), lock.getLockedAt());
        try {
            messageOutbox.enqueue(
                    FinalSubmissionMessageContract.TOPIC,
                    FinalSubmissionMessageContract.EVENT_TYPE,
                    envelopeFactory.create(
                            FinalSubmissionMessageContract.EVENT_TYPE,
                            "submission-lock",
                            lock.getTeamId().toString(),
                            FinalSubmissionMessageContract.idempotencyKey(
                                    lock.getTeamId(), lock.getSubmissionId()),
                            currentTraceId(),
                            payload));
        } catch (DuplicateKeyException ignored) {}

        return getAdminSubmissionDetail(submissionId);
    }

    /** 管理端作废指定提交。 */
    @Transactional
    public void adminInvalidateSubmission(Long submissionId) {
        Submission sub = requiredSubmission(submissionId);
        sub.setStatus("FAILED");
        submissionMapper.updateById(sub);

        lockMapper.delete(new LambdaQueryWrapper<SubmissionLock>().eq(SubmissionLock::getSubmissionId, submissionId));
    }

    /** 管理端重新派发评审任务。 */
    public void adminRedispatchReview(Long submissionId) {
        Submission sub = requiredSubmission(submissionId);
        uploadPersistenceService.enqueueReviewTask(sub);
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }
}
