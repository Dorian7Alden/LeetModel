package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageContentTypes;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.ProblemAttachment;
import com.leetmodel.problem.mapper.ProblemAttachmentMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.service.impl.StorageConsoleServiceImpl;
import com.leetmodel.problem.vo.StorageObjectVO;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端存储桶对象服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class StorageConsoleServiceTest {

    @Mock private MinioClient minioClient;
    @Mock private MinioProperties minioProperties;
    @Mock private StorageService storageService;
    @Mock private ProblemAttachmentMapper attachmentMapper;
    @Mock private ProblemMapper problemMapper;

    @InjectMocks
    private StorageConsoleServiceImpl storageConsoleService;

    @Test
    @DisplayName("手动上传压缩包时使用规范化前缀与压缩包白名单")
    void uploadArchiveUsesNormalizedPrefixAndArchivePolicy() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "materials.zip",
                "application/zip",
                new byte[]{1, 2, 3}
        );
        when(storageService.upload(file, "manual/materials", StorageContentTypes.ARCHIVE))
                .thenReturn("manual/materials/random.zip");
        when(storageService.getUrl("manual/materials/random.zip"))
                .thenReturn("http://minio.local/temporary-url");

        StorageObjectVO result = storageConsoleService.uploadObject(file, " manual/materials ");

        assertEquals("manual/materials/random.zip", result.getObjectKey());
        assertEquals("materials.zip", result.getFileName());
        assertEquals(0, result.getRefCount());
        assertEquals("http://minio.local/temporary-url", result.getDownloadUrl());
    }

    @Test
    @DisplayName("手动上传拒绝相对路径前缀")
    void uploadRejectsRelativePathPrefix() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "materials.zip",
                "application/zip",
                new byte[]{1}
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storageConsoleService.uploadObject(file, "manual/../avatars")
        );

        assertTrue(exception.getMessage().contains("存储前缀"));
        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    @DisplayName("题目附件仍在引用时禁止物理删除存储对象")
    void deleteBlocksKnownProblemAttachmentReference() {
        ProblemAttachment attachment = new ProblemAttachment();
        attachment.setProblemId(12L);
        attachment.setObjectKey("problems/12/attachments/data.zip");
        Problem problem = new Problem();
        problem.setId(12L);
        problem.setTitle("2026 A 题");
        when(attachmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of(attachment));
        when(problemMapper.selectById(12L)).thenReturn(problem);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> storageConsoleService.deleteObjectSafely("problems/12/attachments/data.zip")
        );

        assertTrue(exception.getMessage().contains("2026 A 题"));
        verify(storageService, never()).delete(any());
    }

    @Test
    @DisplayName("未被题目附件引用的对象允许管理员确认后删除")
    void deleteAllowsObjectWithoutKnownProblemReference() {
        when(attachmentMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        storageConsoleService.deleteObjectSafely(" manual/banner.png ");

        verify(storageService).delete(eq("manual/banner.png"));
    }
}
