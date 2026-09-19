package com.leetmodel.file.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileUploadCreateRequestDTO;
import com.leetmodel.common.api.dto.FileUploadPartUrlDTO;
import com.leetmodel.common.api.dto.FileUploadSessionDTO;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.file.config.FileUploadProperties;
import com.leetmodel.file.entity.FileAsset;
import com.leetmodel.file.entity.FileUploadSession;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.mapper.FileUploadSessionMapper;
import com.leetmodel.file.service.impl.FileUploadServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadServiceTest {
    private static final long PART_SIZE = 5L * 1024 * 1024;

    private FileUploadSessionMapper sessionMapper;
    private FileAssetMapper assetMapper;
    private StorageService storageService;
    private FileUploadServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "file-upload-test"),
                FileUploadSession.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "file-upload-asset-test"),
                FileAsset.class);
        sessionMapper = org.mockito.Mockito.mock(FileUploadSessionMapper.class);
        assetMapper = org.mockito.Mockito.mock(FileAssetMapper.class);
        storageService = org.mockito.Mockito.mock(StorageService.class);
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setBucket("leetmodel");
        FileUploadProperties properties = new FileUploadProperties();
        properties.setMaxFileSize(64L * 1024 * 1024);
        properties.setMinPartSize(PART_SIZE);
        properties.setDefaultPartSize(PART_SIZE);
        service = new FileUploadServiceImpl(sessionMapper, assetMapper, storageService,
                minioProperties, properties);
        ReflectionTestUtils.setField(service, "deleteGrace", Duration.ofHours(24));
    }

    @Test
    @DisplayName("创建直传会话时按分片大小计算分片数量")
    void createComputesPartCount() {
        when(assetMapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(7001L);
            return 1;
        });
        when(sessionMapper.insert(any(FileUploadSession.class))).thenReturn(1);

        FileUploadSessionDTO session = service.create(new FileUploadCreateRequestDTO(
                "PROBLEM_ATTACHMENT", "2026", "data.csv", "text/csv",
                PART_SIZE * 2 + 10, null, 9L));

        assertThat(session.partCount()).isEqualTo(3);
        assertThat(session.partSize()).isEqualTo(PART_SIZE);
        assertThat(session.status()).isEqualTo("UPLOADING");
        ArgumentCaptor<FileAsset> assetCaptor = ArgumentCaptor.forClass(FileAsset.class);
        verify(assetMapper).insert(assetCaptor.capture());
        assertThat(assetCaptor.getValue().getLifecycleStatus()).isEqualTo("UPLOADING");
        assertThat(assetCaptor.getValue().getSourceType()).isEqualTo("PROBLEM_ATTACHMENT");
    }

    @Test
    @DisplayName("超出允许大小的文件拒绝创建会话")
    void createRejectsOversizeFile() {
        assertThatThrownBy(() -> service.create(new FileUploadCreateRequestDTO(
                "PROBLEM_ATTACHMENT", null, "huge.csv", "text/csv",
                128L * 1024 * 1024, null, 9L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出");
        verify(assetMapper, never()).insert(any(FileAsset.class));
    }

    @Test
    @DisplayName("分片序号越界时拒绝签发上传地址")
    void partUrlRejectsOutOfRange() {
        when(sessionMapper.selectOne(any())).thenReturn(session("UPLOADING", 2));

        assertThatThrownBy(() -> service.partUrl("token", 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分片序号");
        verify(storageService, never()).getUploadUrl(anyString());
    }

    @Test
    @DisplayName("分片未全部上传时拒绝合并")
    void completeRejectsMissingPart() {
        when(sessionMapper.selectOne(any())).thenReturn(session("UPLOADING", 2));
        when(sessionMapper.claim(any(), anyString())).thenReturn(1);
        when(storageService.sizeOf(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.endsWith("part-1") ? PART_SIZE : -1L;
        });

        assertThatThrownBy(() -> service.complete("token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分片");
        verify(storageService, never()).composeObjects(anyString(), anyList());
    }

    @Test
    @DisplayName("分片齐备时合并并置为未绑定可用资产")
    void completeComposesAndActivatesAsset() {
        when(sessionMapper.selectOne(any())).thenReturn(session("UPLOADING", 2));
        when(sessionMapper.claim(any(), anyString())).thenReturn(1);
        when(storageService.sizeOf(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return key.startsWith("upload-sessions/") ? PART_SIZE : PART_SIZE * 2;
        });
        when(storageService.download(anyString()))
                .thenReturn(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(assetMapper.selectById(7001L)).thenReturn(asset());

        FileAssetSummaryDTO summary = service.complete("token");

        assertThat(summary.lifecycleStatus()).isEqualTo("AVAILABLE_UNBOUND");
        verify(storageService).composeObjects(anyString(), anyList());
        verify(sessionMapper).finish(any(), org.mockito.ArgumentMatchers.eq("COMPLETED"), anyInt());
    }

    @Test
    @DisplayName("过期会话清理标记为 EXPIRED 并删除资产")
    void cleanupExpiredSessionsMarksExpired() {
        FileUploadSession expired = session("UPLOADING", 1);
        expired.setExpireTime(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectList(any())).thenReturn(List.of(expired));

        int handled = service.cleanupExpiredSessions();

        assertThat(handled).isEqualTo(1);
        verify(sessionMapper).finish(any(), org.mockito.ArgumentMatchers.eq("EXPIRED"), anyInt());
        verify(assetMapper).markPhysicallyDeleted(7001L);
    }

    private FileUploadSession session(String status, int partCount) {
        FileUploadSession session = new FileUploadSession();
        session.setId(500L);
        session.setSessionToken("token");
        session.setFileId(7001L);
        session.setObjectKey("problem/2026/paper.pdf");
        session.setPartPrefix("upload-sessions/token/part-");
        session.setOriginalName("paper.pdf");
        session.setContentType("application/pdf");
        session.setPartSize(PART_SIZE);
        session.setPartCount(partCount);
        session.setFileSize(PART_SIZE * partCount);
        session.setStatus(status);
        session.setExpireTime(LocalDateTime.now().plusHours(1));
        return session;
    }

    private FileAsset asset() {
        FileAsset asset = new FileAsset();
        asset.setId(7001L);
        asset.setNamespaceCode("problem");
        asset.setSourceType("PROBLEM_ATTACHMENT");
        asset.setOriginalName("paper.pdf");
        asset.setContentType("text/csv");
        asset.setFileSize(PART_SIZE * 2);
        asset.setLifecycleStatus("AVAILABLE_UNBOUND");
        return asset;
    }
}
