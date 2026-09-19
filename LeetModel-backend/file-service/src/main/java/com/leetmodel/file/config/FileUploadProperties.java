package com.leetmodel.file.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 预签名分片直传配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadProperties {

    /** 单文件最大字节数，默认 5 GiB。 */
    private long maxFileSize = 5L * 1024 * 1024 * 1024;

    /** 单个分片最小字节数，受对象存储最小合并单元约束，默认 5 MiB。 */
    private long minPartSize = 5L * 1024 * 1024;

    /** 未指定分片大小时使用的默认分片字节数，默认 16 MiB。 */
    private long defaultPartSize = 16L * 1024 * 1024;

    /** 单个文件允许的最大分片数，默认 10000。 */
    private int maxPartCount = 10000;

    /** 上传会话保留时长，默认 24 小时。 */
    private java.time.Duration sessionExpiry = java.time.Duration.ofHours(24);

    /** 超过该字节数时跳过合并后内容摘要复核，默认 64 MiB。 */
    private long sha256VerifyMaxBytes = 64L * 1024 * 1024;
}
