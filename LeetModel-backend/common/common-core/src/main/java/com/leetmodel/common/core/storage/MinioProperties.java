package com.leetmodel.common.core.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 对象存储配置属性。
 *
 * <p>在 application.yml 中配置：
 * <pre>{@code
 * minio:
 *   enabled: true
 *   endpoint: http://localhost:9000
 *   access-key: minioadmin
 *   secret-key: minioadmin
 *   bucket: leetmodel
 * }</pre>
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务地址（含协议和端口），如 http://localhost:9000 */
    private String endpoint = "http://localhost:9000";

    /** Access Key */
    private String accessKey = "minioadmin";

    /** Secret Key */
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
