package com.leetmodel.common.core.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端自动配置类。
 *
 * <p>仅在 minio.enabled=true 时生效，按需装配单例 MinioClient；未配置存储的服务在启动时安全跳过。</p>
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MinioConfig {

    /** MinIO 基础连接与容量属性 */
    private final MinioProperties properties;

    /**
     * 构建基于配置属性的 MinIO 客户端实例。
     *
     * @return 线程安全的 MinioClient 单例对象
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
