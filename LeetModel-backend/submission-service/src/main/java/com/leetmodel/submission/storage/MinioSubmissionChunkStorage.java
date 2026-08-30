package com.leetmodel.submission.storage;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.storage.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * MinIO 论文临时分片存储实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioSubmissionChunkStorage implements SubmissionChunkStorage {
    private final MinioClient minioClient;
    private final MinioProperties properties;

    /**
     * 上传一个临时分片。
     * @param objectName 分片对象路径
     * @param file 分片文件
     */
    @Override
    public void upload(String objectName, MultipartFile file) {
        ensureBucketExists();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType("application/octet-stream")
                            .build()
            );
        } catch (Exception exception) {
            log.error("上传论文分片失败: {}", objectName, exception);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 在 MinIO 内部合并分片，避免应用实例缓冲完整 PDF。
     * @param targetObjectName 最终对象路径
     * @param sourceObjectNames 有序分片对象路径
     */
    @Override
    public void compose(String targetObjectName, List<String> sourceObjectNames) {
        BusinessException.throwIf(sourceObjectNames.isEmpty(), ErrorCodeEnum.PARAM_INVALID);
        ensureBucketExists();
        try {
            if (sourceObjectNames.size() == 1) {
                copySingleObject(targetObjectName, sourceObjectNames.get(0));
                return;
            }
            List<ComposeSource> sources = sourceObjectNames.stream()
                    .map(objectName -> ComposeSource.builder()
                            .bucket(properties.getBucket())
                            .object(objectName)
                            .build())
                    .toList();
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(targetObjectName)
                            .sources(sources)
                            .headers(java.util.Map.of("Content-Type", "application/pdf"))
                            .build()
            );
        } catch (Exception exception) {
            log.error("合并论文分片失败: {}", targetObjectName, exception);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 删除临时分片对象。
     * @param objectNames 对象路径列表
     */
    @Override
    public void delete(List<String> objectNames) {
        for (String objectName : objectNames) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(properties.getBucket())
                                .object(objectName)
                                .build()
                );
            } catch (Exception exception) {
                log.error("删除论文临时分片失败: {}", objectName, exception);
            }
        }
    }

    /**
     * 复制单分片文件为最终 PDF。
     * @param targetObjectName 最终对象路径
     * @param sourceObjectName 分片对象路径
     * @throws Exception MinIO 调用失败
     */
    private void copySingleObject(String targetObjectName, String sourceObjectName) throws Exception {
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(targetObjectName)
                        .source(CopySource.builder()
                                .bucket(properties.getBucket())
                                .object(sourceObjectName)
                                .build())
                        .headers(java.util.Map.of("Content-Type", "application/pdf"))
                        .build()
        );
    }

    /**
     * 确保对象存储桶已创建。
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build()
            );
            if (exists) return;
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(properties.getBucket()).build()
            );
        } catch (Exception exception) {
            log.error("初始化论文分片存储失败", exception);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
}
