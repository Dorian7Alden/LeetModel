package com.leetmodel.submission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 论文分片上传会话。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("submission_upload")
public class SubmissionUpload extends BaseEntity {
    private String uploadToken;
    private Long teamId;
    private Long problemId;
    private Long uploaderId;
    private String originalFilename;
    private Long fileSize;
    private String fileSha256;
    private Long chunkSize;
    private Integer totalChunks;
    private String status;
    private Integer activeMarker;
    private String finalObjectName;
    private Long submissionId;
    private LocalDateTime expiresAt;
    private LocalDateTime completingAt;
}
