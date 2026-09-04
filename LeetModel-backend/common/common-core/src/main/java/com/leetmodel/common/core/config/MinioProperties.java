package com.leetmodel.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置属性。
 *
 * <p>映射 application.yml 中 minio.* 前缀的存储桶、访问端点与鉴权密钥。</p>
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址（含协议和端口） */
    private String endpoint = "http://localhost:9000";

    /** 访问标识 Access Key */
    private String accessKey = "minioadmin";
    /** 访问密钥 Secret Key */
    private String secretKey = "minioadmin";

    /** 默认 Bucket 名称 */
    private String bucket = "leetmodel";

    /** 单文件最大大小（字节），默认 10MB */
    private long maxFileSize = 10 * 1024 * 1024;

    /** 上传文件有效期（秒），获取预签名 URL 时使用，默认 7 天 */
    private int expirySeconds = 7 * 24 * 60 * 60;

    /** 是否启用对象存储（设为 false 时跳过 Bean 创建） */
    private boolean enabled = false;
}
