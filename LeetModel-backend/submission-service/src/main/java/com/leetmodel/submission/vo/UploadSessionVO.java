package com.leetmodel.submission.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论文分片上传会话。
 */
@Data
@Builder
public class UploadSessionVO {
    private String uploadId;
    private Long teamId;
    private String originalFilename;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;
    private List<Integer> uploadedChunks;
    private String status;
    private LocalDateTime expiresAt;
    private Long submissionId;
}
