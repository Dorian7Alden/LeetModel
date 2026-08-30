package com.leetmodel.submission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 论文上传会话已接收的临时分片。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("submission_upload_chunk")
public class SubmissionUploadChunk extends BaseEntity {
    private Long uploadId;
    private Integer chunkIndex;
    private Long chunkSize;
    private String chunkSha256;
    private String objectName;
}
