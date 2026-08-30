package com.leetmodel.submission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionLock;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.vo.SubmissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionMapper submissionMapper;
    private final SubmissionLockMapper lockMapper;
    private final TeamFeignClient teamFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final ProblemFeignClient problemFeignClient;
    private final StorageService storageService;

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

    @Transactional
    public SubmissionVO lockFinal(Long teamId, Long userId) {
        TeamDTO team = requiredMemberTeam(teamId, userId);
        BusinessException.throwIf(!"ENDED".equals(team.getPracticeStatus())
                        && (team.getDeadlineAt() == null || LocalDateTime.now().isBefore(team.getDeadlineAt())),
                SubmissionErrorCode.DEADLINE_NOT_REACHED);
        return lockFinal(team);
    }

    @Scheduled(fixedDelayString = "${submission.finalizer.delay-ms:60000}")
    public void finalizeExpiredPractices() {
        Result<List<TeamDTO>> response = teamFeignClient.listExpiredPractices();
        if (response == null || !response.isSuccess() || response.getData() == null) return;
        for (TeamDTO team : response.getData()) {
            try {
                lockFinal(team);
            } catch (BusinessException ignored) {
                // 没有成功提交的队伍保持原状态，等待人工处理。
            }
        }
    }

    private SubmissionVO lockFinal(TeamDTO team) {
        Long teamId = team.getId();
        SubmissionLock existing = lockMapper.selectOne(new LambdaQueryWrapper<SubmissionLock>()
                .eq(SubmissionLock::getTeamId, teamId));
        if (existing != null) return toVO(requiredSubmission(existing.getSubmissionId()), existing.getSubmissionId());
        LocalDateTime effectiveEnd = team.getEndedAt() != null ? team.getEndedAt() : team.getDeadlineAt();
        Submission latest = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTeamId, teamId).eq(Submission::getStatus, "SUCCESS")
                .le(Submission::getCreateTime, effectiveEnd).orderByDesc(Submission::getVersion).last("LIMIT 1"));
        BusinessException.throwIf(latest == null, SubmissionErrorCode.FINAL_SUBMISSION_NOT_FOUND);
        SubmissionLock lock = new SubmissionLock();
        lock.setTeamId(teamId); lock.setSubmissionId(latest.getId()); lock.setLockedAt(LocalDateTime.now());
        lockMapper.insert(lock);
        return toVO(latest, latest.getId());
    }

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
     * 幂等触发提交的 AI 评审任务并转换响应。
     * @param submission 提交记录
     * @return 提交响应
     */
    public SubmissionVO triggerReview(Submission submission) {
        Result<Long> task = reviewFeignClient.createTask(
                submission.getId(),
                submission.getTeamId(),
                submission.getProblemId()
        );
        BusinessException.throwIf(
                task == null || !task.isSuccess(),
                SubmissionErrorCode.REVIEW_TASK_CREATE_FAILED
        );
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
}
