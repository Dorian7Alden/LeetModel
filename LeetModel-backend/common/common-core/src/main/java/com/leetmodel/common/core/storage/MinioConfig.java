package com.leetmodel.common.core.storage;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端自动配置。
 *
 * <p>仅在 minio.enabled=true 时生效。不连接 MinIO 的服务无需配置此属性，
 * Bean 不会被创建，避免启动失败。</p>
 *
 * <h3>面试考点</h3>
 * <ul>
 *   <li><b>@ConditionalOnProperty</b>：条件装配，根据配置文件决定是否创建 Bean。
 *       比 @Profile 更灵活，因为不绑定环境名。</li>
 *   <li><b>MinioClient 线程安全性</b>：MinioClient 是线程安全的，可作为单例 Bean
 *       在整个应用中复用，不需要每次调用创建新实例。</li>
 *   <li><b>预签名 URL</b>：MinIO 支持生成预签名 URL（Presigned URL），前端可直接
 *       通过该 URL 上传/下载文件，绕过业务服务中转，减轻带宽压力。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
