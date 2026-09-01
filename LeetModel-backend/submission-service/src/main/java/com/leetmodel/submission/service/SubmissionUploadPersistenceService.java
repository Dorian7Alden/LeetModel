package com.leetmodel.submission.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.api.dto.ReviewTaskReadyPayload;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionUpload;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.mapper.SubmissionUploadMapper;
import com.leetmodel.submission.messaging.ReviewTaskMessageContract;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 论文上传完成阶段的短事务持久化服务。
 */
@Service
@RequiredArgsConstructor
public class SubmissionUploadPersistenceService {
    private final SubmissionUploadMapper uploadMapper;
    private final SubmissionMapper submissionMapper;
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageOutbox messageOutbox;

    /**
     * 为已合并的上传会话幂等创建提交版本。
     * @param uploadId 上传会话内部 ID
     * @return 已创建或既有的提交记录
     */
    @Transactional
    public Submission createSubmission(Long uploadId) {
        // 锁定上传会话，保证重复完成只创建一个版本
        SubmissionUpload upload = uploadMapper.selectForUpdate(uploadId);
        BusinessException.throwIf(upload == null, SubmissionErrorCode.UPLOAD_NOT_FOUND);
        if (upload.getSubmissionId() != null) {
            Submission existing = submissionMapper.selectById(upload.getSubmissionId());
            BusinessException.throwIf(existing == null, SubmissionErrorCode.SUBMISSION_NOT_FOUND);
            // 兼容升级前已创建提交、但尚未派发评审的记录；重复补偿由唯一幂等键收敛。
            enqueueReviewTask(existing);
            return existing;
        }

        // 生成队伍的下一个提交版本
        Submission submission = new Submission();
        submission.setTeamId(upload.getTeamId());
        submission.setProblemId(upload.getProblemId());
        submission.setSubmitterId(upload.getUploaderId());
        submission.setVersion(submissionMapper.selectMaxVersion(upload.getTeamId()) + 1);
        submission.setOriginalFilename(upload.getOriginalFilename());
        submission.setObjectName(upload.getFinalObjectName());
        submission.setFileSize(upload.getFileSize());
        submission.setStatus("SUCCESS");
        submissionMapper.insert(submission);

        // 同一事务写上传关联和评审 Outbox，Broker 故障不影响提交事实
        uploadMapper.linkSubmission(upload.getId(), submission.getId());
        enqueueReviewTask(submission);
        return submission;
    }

    private void enqueueReviewTask(Submission submission) {
        ReviewTaskReadyPayload payload = new ReviewTaskReadyPayload(
                submission.getId(), submission.getTeamId(), submission.getProblemId(),
                ReviewTaskMessageContract.WORKFLOW_VERSION);
        MessageEnvelopeV1<ReviewTaskReadyPayload> envelope = envelopeFactory.create(
                ReviewTaskMessageContract.EVENT_TYPE,
                "submission",
                submission.getId().toString(),
                ReviewTaskMessageContract.idempotencyKey(
                        submission.getId(), ReviewTaskMessageContract.WORKFLOW_VERSION),
                currentTraceId(),
                payload);
        try {
            messageOutbox.enqueue(
                    ReviewTaskMessageContract.TOPIC,
                    ReviewTaskMessageContract.EVENT_TYPE,
                    envelope);
        } catch (DuplicateKeyException ignored) {
            // 上传行已加锁；唯一键冲突只表示相同业务事件已经进入 Outbox。
        }
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }
}
