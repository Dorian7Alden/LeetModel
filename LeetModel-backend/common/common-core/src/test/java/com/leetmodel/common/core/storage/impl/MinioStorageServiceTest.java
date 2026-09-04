package com.leetmodel.common.core.storage.impl;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinIO 存储服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private MinioProperties properties;

    @InjectMocks
    private MinioStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("minioadmin");
        properties.setSecretKey("minioadmin");
        properties.setBucket("test-bucket");
        properties.setMaxFileSize(10 * 1024 * 1024);

        storageService = new MinioStorageServiceImpl(minioClient, properties);
    }

    @Test
    @DisplayName("上传空文件应抛出异常")
    void uploadEmptyFileThrows() {
        MultipartFile emptyFile = new MockMultipartFile("file", "test.txt",
                "text/plain", new byte[0]);

        assertThrows(BusinessException.class, () -> storageService.upload(emptyFile));
    }

    @Test
    @DisplayName("空文件引用应抛出异常")
    void uploadNullFileThrows() {
        assertThrows(BusinessException.class, () -> storageService.upload(null));
    }

    @Test
    @DisplayName("不支持的文件类型应抛出异常")
    void uploadInvalidContentTypeThrows() {
        MultipartFile invalidFile = new MockMultipartFile("file", "test.exe",
                "application/x-msdownload", "data".getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> storageService.upload(invalidFile));
        assertTrue(ex.getMessage().contains("不支持的文件类型"));
    }

    @Test
    @DisplayName("文件大小超限应抛出异常")
    void uploadOversizedFileThrows() {
        properties.setMaxFileSize(1); // 1 byte limit
        storageService = new MinioStorageServiceImpl(minioClient, properties);

        byte[] content = "this is a large file content".getBytes();
        MultipartFile largeFile = new MockMultipartFile("file", "test.txt",
                "text/plain", content);

        assertThrows(BusinessException.class, () -> storageService.upload(largeFile));
    }

    @Test
    @DisplayName("首次上传时自动创建 Bucket")
    void uploadCreatesMissingBucket() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "image-data".getBytes()
        );
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        String objectName = storageService.upload(file, "avatars");

        assertTrue(objectName.startsWith("avatars/"));
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }
}
