package com.leetmodel.file.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leetmodel.common.api.dto.FileBindingChangedPayload;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.file.entity.FileAsset;
import com.leetmodel.file.entity.FileBinding;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.mapper.FileBindingMapper;
import com.leetmodel.file.service.impl.FileBindingServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileBindingServiceTest {
    private FileBindingMapper bindingMapper;
    private FileAssetMapper assetMapper;
    private FileBindingServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "file-asset-binding-test"),
                FileAsset.class
        );
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "file-binding-test"),
                FileBinding.class
        );
        bindingMapper = mock(FileBindingMapper.class);
        assetMapper = mock(FileAssetMapper.class);
        service = new FileBindingServiceImpl(bindingMapper, assetMapper);
        ReflectionTestUtils.setField(service, "deleteGrace", Duration.ofHours(24));
    }

    @Test
    void bindRecordsActiveProjectionAndActivatesAsset() {
        when(assetMapper.selectById(5L)).thenReturn(businessAsset(5L));
        when(bindingMapper.selectByReference(5L, "problem-service", "PROBLEM_ATTACHMENT", "77"))
                .thenReturn(null);
        when(bindingMapper.insert(any(FileBinding.class))).thenReturn(1);

        service.bind(new FileBindingChangedPayload(5L, "problem-service", "PROBLEM_ATTACHMENT", "77", 1L));

        ArgumentCaptor<FileBinding> captor = ArgumentCaptor.forClass(FileBinding.class);
        verify(bindingMapper).insert(captor.capture());
        assertThat(captor.getValue().getBindingStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getEventVersion()).isEqualTo(1L);
        verify(assetMapper).update(any(), any());
    }

    @Test
    void bindRejectsForeignOwnerService() {
        when(assetMapper.selectById(5L)).thenReturn(businessAsset(5L));

        assertThatThrownBy(() -> service.bind(
                new FileBindingChangedPayload(5L, "user-service", "PROBLEM_ATTACHMENT", "77", 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");
        verify(bindingMapper, never()).insert(any(FileBinding.class));
    }

    @Test
    void bindRejectsAssetWithoutManagedPurpose() {
        FileAsset manual = new FileAsset();
        manual.setId(9L);
        manual.setSourceType("MANUAL");
        when(assetMapper.selectById(9L)).thenReturn(manual);

        assertThatThrownBy(() -> service.bind(
                new FileBindingChangedPayload(9L, "problem-service", "PROBLEM_ATTACHMENT", "77", 1L)))
                .isInstanceOf(BusinessException.class);
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void releaseKeepsAssetActiveWhileOtherBindingsRemain() {
        when(assetMapper.selectById(5L)).thenReturn(businessAsset(5L));
        when(bindingMapper.selectByReference(any(), any(), any(), any())).thenReturn(existingBinding());
        when(bindingMapper.advanceVersion(any(), any(), any(), any())).thenReturn(1);
        when(bindingMapper.countActiveByFileId(5L)).thenReturn(1L);

        service.release(new FileBindingChangedPayload(5L, "problem-service", "PROBLEM_ATTACHMENT", "77", 2L));

        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void releaseSchedulesCleanupWhenLastBindingRemoved() {
        when(assetMapper.selectById(5L)).thenReturn(businessAsset(5L));
        when(bindingMapper.selectByReference(any(), any(), any(), any())).thenReturn(existingBinding());
        when(bindingMapper.advanceVersion(any(), any(), any(), any())).thenReturn(1);
        when(bindingMapper.countActiveByFileId(5L)).thenReturn(0L);

        service.release(new FileBindingChangedPayload(5L, "problem-service", "PROBLEM_ATTACHMENT", "77", 2L));

        verify(assetMapper).update(any(), any());
    }

    @Test
    void staleBoundEventDoesNotReactivateAsset() {
        FileBinding released = existingBinding();
        released.setBindingStatus("RELEASED");
        released.setEventVersion(2L);
        when(assetMapper.selectById(5L)).thenReturn(businessAsset(5L));
        when(bindingMapper.selectByReference(any(), any(), any(), any())).thenReturn(released);
        when(bindingMapper.advanceVersion(any(), any(), any(), any())).thenReturn(0);

        service.bind(new FileBindingChangedPayload(5L, "problem-service", "PROBLEM_ATTACHMENT", "77", 1L));

        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void bindingEventTakesOverDiscoveredAsset() {
        FileAsset discovered = new FileAsset();
        discovered.setId(7L);
        discovered.setSourceType("DISCOVERED");
        discovered.setLifecycleStatus("DISCOVERED");
        FileAsset promoted = new FileAsset();
        promoted.setId(7L);
        promoted.setSourceType("PROBLEM_ATTACHMENT");
        promoted.setLifecycleStatus("AVAILABLE_UNBOUND");
        when(assetMapper.selectById(7L)).thenReturn(discovered, promoted);
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(bindingMapper.selectByReference(any(), any(), any(), any())).thenReturn(null);
        when(bindingMapper.insert(any(FileBinding.class))).thenReturn(1);

        service.bind(new FileBindingChangedPayload(
                7L, "problem-service", "PROBLEM_ATTACHMENT", "88", 1L));

        // 第一次更新用于按声明用途接管 DISCOVERED 资产，第二次把资产置为已绑定
        verify(assetMapper, times(2)).update(any(), any());
        verify(bindingMapper).insert(any(FileBinding.class));
        assertThat(promoted.getSourceType()).isEqualTo("PROBLEM_ATTACHMENT");
    }

    @Test
    void unbindingDiscoveredAssetSchedulesCleanup() {
        FileAsset discovered = new FileAsset();
        discovered.setId(8L);
        discovered.setSourceType("DISCOVERED");
        discovered.setLifecycleStatus("DISCOVERED");
        FileAsset promoted = new FileAsset();
        promoted.setId(8L);
        promoted.setSourceType("USER_AVATAR");
        promoted.setLifecycleStatus("AVAILABLE_UNBOUND");
        when(assetMapper.selectById(8L)).thenReturn(discovered, promoted);
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(bindingMapper.selectByReference(any(), any(), any(), any())).thenReturn(null);
        when(bindingMapper.insert(any(FileBinding.class))).thenReturn(1);
        when(bindingMapper.countActiveByFileId(8L)).thenReturn(0L);

        service.release(new FileBindingChangedPayload(
                8L, "user-service", "USER_AVATAR", "1001", 2L));

        // 接管写入一次，最后一个引用解除后再写入一次待清理状态
        verify(assetMapper, times(2)).update(any(), any());
    }

    private FileAsset businessAsset(Long id) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setSourceType("PROBLEM_ATTACHMENT");
        asset.setLifecycleStatus("AVAILABLE_UNBOUND");
        return asset;
    }

    private FileBinding existingBinding() {
        FileBinding binding = new FileBinding();
        binding.setId(55L);
        binding.setFileId(5L);
        binding.setBindingStatus("ACTIVE");
        binding.setEventVersion(1L);
        return binding;
    }
}
