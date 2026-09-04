package com.leetmodel.common.core.config;

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
