package com.leetmodel.submission.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionUpload;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.mapper.SubmissionUploadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 论文上传完成阶段的短事务持久化服务。
 */
@Service
@RequiredArgsConstructor
public class SubmissionUploadPersistenceService {
    private final SubmissionUploadMapper uploadMapper;
    private final SubmissionMapper submissionMapper;

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

        // 上传会话保留提交关联，供后续评审触发重试
        uploadMapper.linkSubmission(upload.getId(), submission.getId());
        return submission;
    }
}
