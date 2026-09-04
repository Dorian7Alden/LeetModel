package com.leetmodel.common.core.storage.impl;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.storage.StorageService;
import io.minio.*;
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
 * <p>采用 {prefix}/{UUID}.{ext} 规则重命名以防御同名冲突与路径遍历，生成有界时效的 GET 预签名访问地址。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
public class MinioStorageServiceImpl implements StorageService {

    /** 允许上传的文件类型 MIME 白名单 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain",                                                               // 纯文本
            "text/markdown",                                                            // Markdown
            "text/csv",                                                                 // CSV 表格
            "application/pdf",                                                          // PDF 文档
            "application/msword",                                                       // Word doc
            "image/jpeg", "image/png", "image/gif", "image/webp",                       // 图片
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // Word docx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"         // Excel xlsx
    );

    /** MinIO 底层 SDK 客户端 */
    private final MinioClient minioClient;
    /** 对象存储配置属性 */
    private final MinioProperties minioProperties;

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

        // 3. 确保 Bucket 存在
        ensureBucketExists();

        // 4. 上传到 MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.atInfo()
                    .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.STORAGE_OPERATION_COMPLETED)
                    .addKeyValue(LogFieldNames.OUTCOME, "upload")
                    .log("Object storage operation completed");
        } catch (Exception e) {
            // 上传失败
            logStorageFailure("upload", e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "文件上传失败");
        }

        return objectName;
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            logStorageFailure("download", e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "文件下载失败");
        }
    }

    @Override
    public String getUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(minioProperties.getExpirySeconds())
                            .build()
            );
        } catch (Exception e) {
            logStorageFailure("presign", e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "获取文件访问链接失败");
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .build()
            );
            log.atInfo()
                    .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.STORAGE_OPERATION_COMPLETED)
                    .addKeyValue(LogFieldNames.OUTCOME, "delete")
                    .log("Object storage operation completed");
        } catch (Exception e) {
            logStorageFailure("delete", e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "文件删除失败");
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 确保配置的存储桶已初始化，若不存在则调用接口自动创建。
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .build()
            );
            if (exists) return;

            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .build()
            );
            log.atInfo()
                    .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.STORAGE_OPERATION_COMPLETED)
                    .addKeyValue(LogFieldNames.OUTCOME, "bucket_create")
                    .log("Object storage operation completed");
        } catch (Exception e) {
            logStorageFailure("bucket_initialize", e);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "对象存储暂不可用");
        }
    }

    /**
     * 记录存储操作失败日志。
     *
     * @param operation 失败的操作名称，如 upload、download、presign
     * @param exception 捕获的底层异常对象
     */
    private void logStorageFailure(String operation, Exception exception) {
        log.atError()
                .setCause(exception)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.STORAGE_OPERATION_FAILED)
                .addKeyValue(LogFieldNames.OUTCOME, operation)
                .log("Object storage operation failed");
    }

    /**
     * 校验待上传文件的完整性、大小上限与类型白名单。
     *
     * @param file 待校验的文件对象
     * @throws BusinessException 当文件为空、超出大小限制或类型不支持时抛出
     */
    private void validateFile(MultipartFile file) {

        // 1. 文件非空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_INVALID, "文件不能为空");
        }
        // 2. 文件大小
        if (file.getSize() > minioProperties.getMaxFileSize()) {
            int maxSizeMB = Math.toIntExact(minioProperties.getMaxFileSize() / 1024 / 1024);
            throw new BusinessException(
                    ErrorCodeEnum.PARAM_INVALID,
                    "文件大小超出限制（最大 " + maxSizeMB + "MB）"
            );
        }
        // 3. 文件类型
        if (file.getContentType() != null && !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(
                    ErrorCodeEnum.PARAM_INVALID,
                    "不支持的文件类型: " + file.getContentType()
            );
        }
    }

    /**
     * 从原始文件名中提取包含点号的后缀扩展名。
     *
     * @param filename 原始文件名，允许为 null
     * @return 提取的扩展名（如 .pdf）；无扩展名时返回空字符串
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
