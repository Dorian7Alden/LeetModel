package com.leetmodel.common.core.storage.impl;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.storage.MinioProperties;
import com.leetmodel.common.core.storage.StorageService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * MinIO 对象存储服务实现。
 *
 * <p>文件命名策略：{prefix}/{UUID}.{原始扩展名}，避免文件名冲突。
 * 预签名 URL 有效期通过配置文件控制，默认 7 天。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
public class MinioStorageServiceImpl implements StorageService {

    /** 允许上传的文件类型 MIME 白名单 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "text/plain", "text/markdown",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"        // .xlsx
    );

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public String upload(MultipartFile file) {
        return upload(file, "files");
    }

    @Override
    public String upload(MultipartFile file, String prefix) {
        // 1. 校验文件
        validateFile(file);

        // 2. 生成 objectName
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        String objectName = prefix + "/" + UUID.randomUUID() + extension;

        // 3. 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("文件上传成功: {} (size={}bytes)", objectName, file.getSize());
        } catch (Exception e) {
            log.error("文件上传失败: {}", objectName, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }

        return objectName;
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("文件下载失败: {}", objectName, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public String getUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(properties.getExpirySeconds())
                            .build()
            );
        } catch (Exception e) {
            log.error("获取预签名 URL 失败: {}", objectName, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "获取文件访问链接失败");
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: {}", objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", objectName, e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "文件删除失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 校验上传文件：非空、大小、类型。
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_INVALID, "文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSize()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_INVALID,
                    "文件大小超出限制（最大 " + properties.getMaxFileSize() / 1024 / 1024 + "MB）");
        }
        if (file.getContentType() != null && !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_INVALID,
                    "不支持的文件类型: " + file.getContentType());
        }
    }

    /**
     * 从原始文件名提取扩展名（含点号），无扩展名时返回空串。
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
