package com.leetmodel.file.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileAssetAdoptRequestDTO;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.file.entity.FileAsset;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.mapper.FileBindingMapper;
import com.leetmodel.file.model.FileAssetVO;
import com.leetmodel.file.model.FileAccessUrlVO;
import com.leetmodel.file.model.FileReconcileVO;
import com.leetmodel.file.service.impl.FileAssetServiceImpl;
import com.leetmodel.file.storage.FileObjectInventory;
import com.leetmodel.file.storage.PhysicalObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAssetServiceTest {
    private FileAssetMapper mapper;
    private FileBindingMapper bindingMapper;
    private StorageService storageService;
    private FileObjectInventory inventory;
    private FileAssetServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "file-asset-test"),
                FileAsset.class
        );
        mapper = mock(FileAssetMapper.class);
        bindingMapper = mock(FileBindingMapper.class);
        storageService = mock(StorageService.class);
        inventory = mock(FileObjectInventory.class);
        MinioProperties properties = new MinioProperties();
        properties.setBucket("leetmodel");
        properties.setExpirySeconds(600);
        service = new FileAssetServiceImpl(mapper, bindingMapper, storageService, inventory, properties);
        ReflectionTestUtils.setField(service, "deleteGrace", Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "retryDelay", Duration.ofMinutes(10));
    }

    @Test
    void uploadRegistersManualAssetWithLogicalGroup() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.png", "image/png", new byte[]{1, 2, 3});
        when(storageService.upload(any(), any(), any())).thenReturn("manual/icons/uuid.png");
        when(mapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(100L);
            return 1;
        });

        FileAssetVO result = service.upload(file, "site/icons", 9L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getGroupPath()).isEqualTo("site/icons");
        assertThat(result.getObjectKey()).isEqualTo("manual/icons/uuid.png");
        assertThat(result.getFileExtension()).isEqualTo("png");
        assertThat(result.getPreviewType()).isEqualTo("IMAGE");
        assertThat(result.isPreviewable()).isTrue();
        assertThat(result.isDeletable()).isTrue();
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("MANUAL");
        assertThat(captor.getValue().getCreatorId()).isEqualTo(9L);
    }

    @Test
    void uploadAllowsMissingContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "README.md", null, new byte[]{1, 2, 3});
        when(storageService.upload(any(), any(), any())).thenReturn("manual/README.md");
        when(mapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(101L);
            return 1;
        });

        FileAssetVO result = service.upload(file, "", 9L);

        assertThat(result.getContentType()).isNull();
        assertThat(result.getPreviewType()).isEqualTo("MARKDOWN");
        assertThat(result.isPreviewable()).isTrue();
    }

    @Test
    void previewUrlUsesTheSameProtectedAssetAccessRules() {
        FileAsset asset = asset(13L, "manual/docs/readme.md", "MANUAL", "ACTIVE");
        asset.setOriginalName("readme.md");
        asset.setContentType("text/markdown");
        when(mapper.selectById(13L)).thenReturn(asset);
        when(storageService.getUrl(asset.getObjectKey())).thenReturn("https://minio.test/readme");

        FileAccessUrlVO result = service.createPreviewUrl(13L);

        assertThat(result.url()).isEqualTo("https://minio.test/readme");
        verify(storageService).getUrl(asset.getObjectKey());
    }

    @Test
    void uploadRejectsInvalidGroupBeforeWritingStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "icon.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> service.upload(file, "../icons", 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分组");
        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void discoveredAssetCannotBeDeleted() {
        FileAsset discovered = asset(10L, "avatars/a.png", "DISCOVERED", "DISCOVERED");
        when(mapper.selectById(10L)).thenReturn(discovered);

        assertThatThrownBy(() -> service.requestDelete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只读");
        verify(mapper, never()).update(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedReconcileDoesNotCreateDuplicateAsset() {
        PhysicalObject physical = new PhysicalObject("legacy/paper.pdf", 20L, LocalDateTime.now());
        when(inventory.listAll()).thenReturn(List.of(physical));
        FileAsset known = asset(11L, physical.objectKey(), "DISCOVERED", "DISCOVERED");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(known));
        when(mapper.insert(any(FileAsset.class))).thenReturn(1);

        FileReconcileVO first = service.reconcile();
        FileReconcileVO second = service.reconcile();

        assertThat(first.created()).isEqualTo(1);
        assertThat(second.created()).isZero();
        verify(mapper, times(1)).insert(any(FileAsset.class));
    }

    @Test
    void reconcileSurfacesObjectStorageFailureWithoutWritingDatabase() {
        when(inventory.listAll()).thenThrow(new BusinessException(
                com.leetmodel.file.enums.FileErrorCode.STORAGE_UNAVAILABLE));

        assertThatThrownBy(service::reconcile)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对象存储");
        verify(mapper, never()).insert(any(FileAsset.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupFailureKeepsMetadataAndSchedulesRetry() {
        FileAsset due = asset(12L, "manual/icon.png", "MANUAL", "PENDING_DELETE");
        due.setRetryCount(0);
        due.setCleanupAfter(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(due));
        doThrow(new RuntimeException("minio down")).when(storageService).delete(due.getObjectKey());

        int cleaned = service.cleanupDueAssets();

        assertThat(cleaned).isZero();
        verify(mapper, never()).markPhysicallyDeleted(any());
        verify(mapper).update(any(), any());
    }

    @Test
    void registerForPurposeStoresBusinessAssetAwaitingBinding() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", new byte[]{1, 2, 3});
        when(storageService.upload(any(), any(), any())).thenReturn("problem/2026/data.csv");
        when(mapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(200L);
            return 1;
        });

        FileAssetSummaryDTO summary = service.registerForPurpose("problem_attachment", null, file, 7L);

        assertThat(summary.fileId()).isEqualTo(200L);
        assertThat(summary.namespaceCode()).isEqualTo("problem");
        assertThat(summary.sourceType()).isEqualTo("PROBLEM_ATTACHMENT");
        assertThat(summary.lifecycleStatus()).isEqualTo("AVAILABLE_UNBOUND");
        ArgumentCaptor<FileAsset> captor = ArgumentCaptor.forClass(FileAsset.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getContentSha256()).hasSize(64);
        assertThat(captor.getValue().getCreatorId()).isEqualTo(7L);
        assertThat(captor.getValue().getCleanupAfter()).isAfter(LocalDateTime.now());
    }

    @Test
    void registerForPurposeRejectsContentTypeOutsidePurpose() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "paper.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.registerForPurpose("user_avatar", null, file, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");
        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void accessUrlIsAvailableForBusinessAssetBeforeBinding() {
        FileAsset asset = asset(21L, "problem/a.pdf", "PROBLEM_ATTACHMENT", "AVAILABLE_UNBOUND");
        when(mapper.selectById(21L)).thenReturn(asset);
        when(storageService.getUrl(asset.getObjectKey())).thenReturn("https://minio.test/a.pdf");

        FileAccessUrlVO result = service.createAccessUrlByFileId(21L);

        assertThat(result.url()).isEqualTo("https://minio.test/a.pdf");
    }

    @Test
    void adoptRegistersHandoverObjectAfterCheckingSize() {
        when(storageService.sizeOf("submission-uploads/token/final.pdf")).thenReturn(2048L);
        when(mapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(300L);
            return 1;
        });

        FileAssetSummaryDTO summary = service.adoptForPurpose(new FileAssetAdoptRequestDTO(
                "SUBMISSION_PAPER", "submission-uploads/token/final.pdf",
                "final.pdf", "application/pdf", 2048L, "2026", 7L));

        assertThat(summary.fileId()).isEqualTo(300L);
        assertThat(summary.sourceType()).isEqualTo("SUBMISSION_PAPER");
        assertThat(summary.lifecycleStatus()).isEqualTo("AVAILABLE_UNBOUND");
    }

    @Test
    void adoptRejectsObjectOutsideHandoverDirectory() {
        assertThatThrownBy(() -> service.adoptForPurpose(new FileAssetAdoptRequestDTO(
                "SUBMISSION_PAPER", "avatars/whatever.png",
                "final.pdf", "application/pdf", 10L, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("交接目录");
        verify(storageService, never()).sizeOf(any());
    }

    @Test
    void adoptRejectsSizeMismatch() {
        when(storageService.sizeOf(any())).thenReturn(999L);

        assertThatThrownBy(() -> service.adoptForPurpose(new FileAssetAdoptRequestDTO(
                "SUBMISSION_PAPER", "submission-uploads/token/final.pdf",
                "final.pdf", "application/pdf", 2048L, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小");
        verify(mapper, never()).insert(any(FileAsset.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupKeepsUnboundAssetThatStillHasActiveBinding() {
        FileAsset unbound = asset(31L, "problem/a.pdf", "PROBLEM_ATTACHMENT", "AVAILABLE_UNBOUND");
        unbound.setCleanupAfter(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(unbound));
        when(bindingMapper.countActiveByFileId(31L)).thenReturn(1L);

        int cleaned = service.cleanupDueAssets();

        assertThat(cleaned).isZero();
        verify(storageService, never()).delete(any());
    }

    private FileAsset asset(Long id, String objectKey, String sourceType, String status) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setBucketName("leetmodel");
        asset.setObjectKey(objectKey);
        asset.setOriginalName(objectKey);
        asset.setFileSize(1L);
        asset.setNamespaceCode(sourceType.toLowerCase());
        asset.setGroupPath("legacy");
        asset.setSourceType(sourceType);
        asset.setLifecycleStatus(status);
        asset.setDeleted(0);
        return asset;
    }
}
