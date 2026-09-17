package com.leetmodel.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_asset")
public class FileAsset extends BaseEntity {
    private String bucketName;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String namespaceCode;
    private String groupPath;
    private String sourceType;
    private String lifecycleStatus;
    private Long creatorId;
    private LocalDateTime deleteRequestedAt;
    private LocalDateTime cleanupAfter;
    private Integer retryCount;
    private String lastError;
}
