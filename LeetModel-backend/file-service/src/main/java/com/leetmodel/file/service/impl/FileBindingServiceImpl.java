package com.leetmodel.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.leetmodel.common.api.dto.FileBindingChangedPayload;
import com.leetmodel.common.api.messaging.FileBindingMessageContract;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.file.entity.FileAsset;
import com.leetmodel.file.entity.FileBinding;
import com.leetmodel.file.enums.FileErrorCode;
import com.leetmodel.file.enums.FilePurpose;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.mapper.FileBindingMapper;
import com.leetmodel.file.service.FileBindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FileBindingServiceImpl implements FileBindingService {
    static final String BINDING_ACTIVE = "ACTIVE";
    static final String BINDING_RELEASED = "RELEASED";
    private static final String ASSET_ACTIVE = "ACTIVE";
    private static final String ASSET_AVAILABLE_UNBOUND = "AVAILABLE_UNBOUND";

    private final FileBindingMapper fileBindingMapper;
    private final FileAssetMapper fileAssetMapper;

    @Value("${file.lifecycle.delete-grace:24h}")
    private Duration deleteGrace;

    @Override
    @Transactional
    public void bind(FileBindingChangedPayload payload) {
        FileAsset asset = requireBindingAsset(payload);
        String appliedStatus = applyBinding(payload, BINDING_ACTIVE, FileBindingMessageContract.BOUND_EVENT_TYPE);
        if (!BINDING_ACTIVE.equals(appliedStatus)) {
            // 版本较旧的绑定事件不会让已经解绑的文件重新进入已绑定状态。
            return;
        }
        fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, asset.getId())
                .in(FileAsset::getLifecycleStatus, ASSET_AVAILABLE_UNBOUND, ASSET_ACTIVE)
                .set(FileAsset::getLifecycleStatus, ASSET_ACTIVE)
                .set(FileAsset::getCleanupAfter, null));
    }

    @Override
    @Transactional
    public void release(FileBindingChangedPayload payload) {
        FileAsset asset = requireBindingAsset(payload);
        applyBinding(payload, BINDING_RELEASED, FileBindingMessageContract.UNBOUND_EVENT_TYPE);
        if (fileBindingMapper.countActiveByFileId(asset.getId()) > 0) {
            return;
        }
        // 最后一个有效引用解除后只进入待清理状态，物理删除由宽限期结束后的清理任务执行。
        fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, asset.getId())
                .eq(FileAsset::getLifecycleStatus, ASSET_ACTIVE)
                .set(FileAsset::getLifecycleStatus, ASSET_AVAILABLE_UNBOUND)
                .set(FileAsset::getCleanupAfter, LocalDateTime.now().plus(deleteGrace)));
    }

    @Override
    public long activeCount(Long fileId) {
        return fileId == null ? 0L : fileBindingMapper.countActiveByFileId(fileId);
    }

    /**
     * 写入或推进引用投影。
     *
     * @param payload 事件载荷
     * @param bindingStatus 目标绑定状态
     * @param eventType 事件类型
     * @return 写入后该业务引用实际生效的状态
     */
    private String applyBinding(FileBindingChangedPayload payload, String bindingStatus, String eventType) {
        String idempotencyKey = FileBindingMessageContract.idempotencyKey(
                eventType, payload.fileId(), payload.resourceId(), payload.eventVersion());
        FileBinding existing = fileBindingMapper.selectByReference(
                payload.fileId(), payload.ownerService(), payload.resourceType(), payload.resourceId());
        if (existing == null) {
            if (insertBinding(payload, bindingStatus, idempotencyKey)) {
                return bindingStatus;
            }
            existing = fileBindingMapper.selectByReference(
                    payload.fileId(), payload.ownerService(), payload.resourceType(), payload.resourceId());
            if (existing == null) {
                throw new BusinessException(FileErrorCode.FILE_BINDING_INVALID, "引用投影写入冲突");
            }
        }
        // 事件版本不前进时保持原状态，保证重复投递与乱序投递幂等。
        int advanced = fileBindingMapper.advanceVersion(
                existing.getId(), bindingStatus, payload.eventVersion(), idempotencyKey);
        return advanced > 0 ? bindingStatus : existing.getBindingStatus();
    }

    private boolean insertBinding(FileBindingChangedPayload payload, String bindingStatus, String idempotencyKey) {
        FileBinding binding = new FileBinding();
        binding.setFileId(payload.fileId());
        binding.setOwnerService(payload.ownerService());
        binding.setResourceType(payload.resourceType());
        binding.setResourceId(payload.resourceId());
        binding.setBindingStatus(bindingStatus);
        binding.setEventVersion(payload.eventVersion());
        binding.setIdempotencyKey(idempotencyKey);
        binding.setDeleted(0);
        try {
            fileBindingMapper.insert(binding);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private FileAsset requireBindingAsset(FileBindingChangedPayload payload) {
        FileAsset asset = fileAssetMapper.selectById(payload.fileId());
        if (asset == null) {
            throw new BusinessException(FileErrorCode.FILE_BINDING_INVALID, "文件资产不存在");
        }
        FilePurpose purpose = FilePurpose.fromSourceType(asset.getSourceType());
        if (purpose == null || !purpose.ownerService().equals(payload.ownerService())) {
            throw new BusinessException(FileErrorCode.FILE_BINDING_INVALID, "文件用途与绑定服务不匹配");
        }
        return asset;
    }
}
