package com.leetmodel.file.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileAssetVO {
    private Long id;
    private String bucketName;
    private String objectKey;
    private String originalName;
    private String fileExtension;
    private String contentType;
    private Long fileSize;
    private String previewType;
    private boolean previewable;
    private String namespaceCode;
    private String groupPath;
    private String sourceType;
    private String lifecycleStatus;
    private Long creatorId;
    private LocalDateTime cleanupAfter;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String lastError;
    private boolean deletable;
}
