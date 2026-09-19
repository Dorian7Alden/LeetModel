package com.leetmodel.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 预签名分片直传会话。
 *
 * <p>会话负责保存客户端直传期间的分片约定；物理分片以对象存储为事实源。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_upload_session")
public class FileUploadSession extends BaseEntity {
    private String sessionToken;
    private Long fileId;
    private String purposeCode;
    private String objectKey;
    private String partPrefix;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Long partSize;
    private Integer partCount;
    private String status;
    private Long creatorId;
    private LocalDateTime expireTime;
    private LocalDateTime completeTime;
}
