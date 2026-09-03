package com.leetmodel.submission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.FinalSubmissionChangedPayload;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionLock;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.messaging.FinalSubmissionMessageContract;
import com.leetmodel.submission.audit.SubmissionAuditEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** 最终提交事实与事件的事务边界。 */
@Service
@RequiredArgsConstructor
public class SubmissionFinalizationPersistenceService {
    private final SubmissionLockMapper lockMapper;
    private final SubmissionMapper submissionMapper;
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageOutbox messageOutbox;
    private final SubmissionAuditEventProducer audit;

    /** 锁定最终提交，并在同一事务写入排行事件 Outbox。 */
    @Transactional
    public Submission lockFinal(TeamDTO team) {
        SubmissionLock existing = lockMapper.selectOne(new LambdaQueryWrapper<SubmissionLock>()
                .eq(SubmissionLock::getTeamId, team.getId()));
        if (existing != null) {
            Submission submission = requiredSubmission(existing.getSubmissionId());
            enqueue(existing, submission);
            return submission;
        }

        LocalDateTime effectiveEnd = team.getEndedAt() != null ? team.getEndedAt() : team.getDeadlineAt();
        Submission latest = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTeamId, team.getId()).eq(Submission::getStatus, "SUCCESS")
                .le(Submission::getCreateTime, effectiveEnd).orderByDesc(Submission::getVersion).last("LIMIT 1"));
        BusinessException.throwIf(latest == null, SubmissionErrorCode.FINAL_SUBMISSION_NOT_FOUND);

        SubmissionLock lock = new SubmissionLock();
        lock.setTeamId(team.getId());
        lock.setSubmissionId(latest.getId());
        lock.setLockedAt(LocalDateTime.now());
        lockMapper.insert(lock);
        enqueue(lock, latest);
        audit.finalized(team, latest.getId(), latest.getVersion());
        return latest;
    }

    private Submission requiredSubmission(Long id) {
        Submission submission = submissionMapper.selectById(id);
        BusinessException.throwIf(submission == null, SubmissionErrorCode.SUBMISSION_NOT_FOUND);
        return submission;
    }

    private void enqueue(SubmissionLock lock, Submission submission) {
        FinalSubmissionChangedPayload payload = new FinalSubmissionChangedPayload(
                submission.getTeamId(), submission.getProblemId(), submission.getId(), lock.getLockedAt());
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
        } catch (DuplicateKeyException ignored) {
            // 同一队伍和最终提交只对应一个业务事件；重复锁定用于补偿历史缺失 Outbox。
        }
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }
}
