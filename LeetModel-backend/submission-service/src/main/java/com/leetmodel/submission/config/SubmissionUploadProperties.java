package com.leetmodel.submission.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 论文分片上传配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "submission.upload")
public class SubmissionUploadProperties {
    private long maxFileSize = 20L * 1024 * 1024;
    private long chunkSize = 5L * 1024 * 1024;
    private long sessionExpiryHours = 24;
    private long completionStaleSeconds = 120;
}
