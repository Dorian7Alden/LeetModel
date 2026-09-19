package com.leetmodel.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileAssetAdoptRequestDTO;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.storage.StorageContentTypes;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.file.entity.FileAsset;
import com.leetmodel.file.enums.FileErrorCode;
import com.leetmodel.file.enums.FilePurpose;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.mapper.FileBindingMapper;
import com.leetmodel.file.model.FileAccessUrlVO;
import com.leetmodel.file.model.FileAssetPageVO;
import com.leetmodel.file.model.FileAssetQuery;
import com.leetmodel.file.model.FileAssetVO;
import com.leetmodel.file.model.FileGroupVO;
import com.leetmodel.file.model.FileReconcileVO;
import com.leetmodel.file.service.FileAssetService;
import com.leetmodel.file.storage.FileObjectInventory;
import com.leetmodel.file.storage.PhysicalObject;
import com.leetmodel.file.support.FileContentDigest;
import com.leetmodel.file.support.FileGroupPath;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FileAssetServiceImpl implements FileAssetService {
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_DISCOVERED = "DISCOVERED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_AVAILABLE_UNBOUND = "AVAILABLE_UNBOUND";
    private static final String STATUS_DISCOVERED = "DISCOVERED";
    private static final String STATUS_PENDING_DELETE = "PENDING_DELETE";
    private static final String STATUS_DELETE_FAILED = "DELETE_FAILED";
    private static final String UNGROUPED_FILTER = "__ungrouped__";
    /** 允许在普通读取链路生成短时效地址的资产状态。 */
    private static final Set<String> ACCESSIBLE_STATUSES =
            Set.of(STATUS_ACTIVE, STATUS_AVAILABLE_UNBOUND, STATUS_DISCOVERED);
    /** 允许被业务服务接管的交接目录前缀；调用方不能借此提交任意物理路由。 */
    private static final Set<String> HANDOVER_PREFIXES = Set.of("submission-uploads/", "submissions/");

    private final FileAssetMapper fileAssetMapper;
    private final FileBindingMapper fileBindingMapper;
    private final StorageService storageService;
    private final FileObjectInventory objectInventory;
    private final MinioProperties minioProperties;

    @Value("${file.lifecycle.delete-grace:24h}")
    private Duration deleteGrace;
    @Value("${file.lifecycle.retry-delay:10m}")
    private Duration retryDelay;

    @Override
    public FileAssetPageVO page(FileAssetQuery query) {
        boolean filterUngrouped = UNGROUPED_FILTER.equals(query.getGroupPath());
        String groupPath = filterUngrouped ? "" : query.getGroupPath();
        LambdaQueryWrapper<FileAsset> pageQuery = new LambdaQueryWrapper<FileAsset>()
                .eq(StringUtils.hasText(groupPath) || filterUngrouped, FileAsset::getGroupPath, groupPath)
                .eq(StringUtils.hasText(query.getLifecycleStatus()),
                        FileAsset::getLifecycleStatus, query.getLifecycleStatus())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> {
                    String keyword = query.getKeyword().trim();
                    wrapper.like(FileAsset::getOriginalName, keyword)
                            .or()
                            .like(FileAsset::getObjectKey, keyword)
                            .or()
                            .like(FileAsset::getContentType, keyword);
                    Long assetId = parsePositiveLong(keyword);
                    if (assetId != null) {
                        wrapper.or().eq(FileAsset::getId, assetId);
                    }
                })
                .orderByDesc(FileAsset::getCreateTime);

        Page<FileAsset> sourcePage = fileAssetMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), pageQuery);
        List<FileAssetVO> rows = sourcePage.getRecords().stream().map(this::toVO).toList();
        PageResult<FileAssetVO> resultPage = new PageResult<>(
                sourcePage.getTotal(), query.getPage(), query.getSize(), rows);

        List<FileAsset> allAssets = fileAssetMapper.selectList(
                new LambdaQueryWrapper<FileAsset>().orderByAsc(FileAsset::getGroupPath));
        long totalBytes = allAssets.stream().mapToLong(asset -> safeSize(asset.getFileSize())).sum();
        long manualFiles = allAssets.stream().filter(asset -> SOURCE_MANUAL.equals(asset.getSourceType())).count();
        long discoveredFiles = allAssets.size() - manualFiles;
        return new FileAssetPageVO(
                allAssets.size(),
                totalBytes,
                manualFiles,
                discoveredFiles,
                aggregateGroups(allAssets),
                resultPage
        );
    }

    @Override
    public FileAssetVO upload(MultipartFile file, String groupPath, Long creatorId) {
        String normalizedGroup = FileGroupPath.normalize(groupPath);
        String prefix = normalizedGroup.isEmpty() ? "manual" : "manual/" + normalizedGroup;
        String objectKey = storageService.upload(file, prefix, StorageContentTypes.ARCHIVE);
        FileAsset asset = new FileAsset();
        asset.setBucketName(minioProperties.getBucket());
        asset.setObjectKey(objectKey);
        asset.setOriginalName(resolveOriginalName(file, objectKey));
        asset.setContentType(file.getContentType());
        asset.setFileSize(file.getSize());
        asset.setContentSha256(FileContentDigest.sha256(file));
        asset.setNamespaceCode("manual");
        asset.setGroupPath(normalizedGroup);
        asset.setAccessLevel("BUSINESS_AUTHORIZED");
        asset.setSourceType(SOURCE_MANUAL);
        asset.setLifecycleStatus(STATUS_ACTIVE);
        asset.setCreatorId(creatorId);
        asset.setRetryCount(0);
        asset.setDeleted(0);
        try {
            fileAssetMapper.insert(asset);
        } catch (RuntimeException exception) {
            compensateUploadedObject(objectKey, exception);
            throw exception;
        }
        return toVO(asset);
    }

    @Override
    public FileAssetSummaryDTO registerForPurpose(String purposeCode, String groupPath,
                                                  MultipartFile file, Long creatorId) {
        FilePurpose purpose = FilePurpose.fromCode(purposeCode);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(FileErrorCode.FILE_CONTENT_TYPE_NOT_ALLOWED, "上传文件不能为空");
        }
        if (!purpose.allowsContentType(file.getContentType())) {
            throw new BusinessException(FileErrorCode.FILE_CONTENT_TYPE_NOT_ALLOWED,
                    "用途 " + purpose.name() + " 不允许类型 " + file.getContentType());
        }
        String normalizedGroup = FileGroupPath.normalize(groupPath);
        String contentSha256 = FileContentDigest.sha256(file);
        String prefix = normalizedGroup.isEmpty()
                ? purpose.namespaceCode()
                : purpose.namespaceCode() + "/" + normalizedGroup;
        String objectKey = storageService.upload(file, prefix, purpose.additionalContentTypes());

        FileAsset asset = new FileAsset();
        asset.setBucketName(minioProperties.getBucket());
        asset.setObjectKey(objectKey);
        asset.setOriginalName(resolveOriginalName(file, objectKey));
        asset.setContentType(file.getContentType());
        asset.setFileSize(file.getSize());
        asset.setContentSha256(contentSha256);
        asset.setNamespaceCode(purpose.namespaceCode());
        asset.setGroupPath(normalizedGroup);
        asset.setAccessLevel(purpose.accessLevel());
        asset.setSourceType(purpose.sourceType());
        asset.setLifecycleStatus(STATUS_AVAILABLE_UNBOUND);
        asset.setCreatorId(creatorId);
        // 登记后等待业务绑定事件；超过宽限期仍无有效引用才允许物理清理。
        asset.setCleanupAfter(LocalDateTime.now().plus(deleteGrace));
        asset.setRetryCount(0);
        asset.setDeleted(0);
        try {
            fileAssetMapper.insert(asset);
        } catch (RuntimeException exception) {
            compensateUploadedObject(objectKey, exception);
            throw exception;
        }
        return toSummary(asset);
    }

    @Override
    public FileAssetSummaryDTO adoptForPurpose(FileAssetAdoptRequestDTO request) {
        FilePurpose purpose = FilePurpose.fromCode(request.purpose());
        if (!purpose.allowsContentType(request.contentType())) {
            throw new BusinessException(FileErrorCode.FILE_CONTENT_TYPE_NOT_ALLOWED,
                    "用途 " + purpose.name() + " 不允许类型 " + request.contentType());
        }
        String objectKey = request.objectKey() == null ? "" : request.objectKey().trim();
        if (!isHandoverObjectKey(objectKey)) {
            throw new BusinessException(FileErrorCode.FILE_OBJECT_KEY_NOT_ALLOWED);
        }
        long actualSize = storageService.sizeOf(objectKey);
        if (actualSize != request.fileSize()) {
            throw new BusinessException(FileErrorCode.FILE_SIZE_MISMATCH);
        }
        String normalizedGroup = FileGroupPath.normalize(request.groupPath());

        FileAsset asset = new FileAsset();
        asset.setBucketName(minioProperties.getBucket());
        asset.setObjectKey(objectKey);
        asset.setOriginalName(request.originalName());
        asset.setContentType(request.contentType());
        asset.setFileSize(actualSize);
        asset.setNamespaceCode(purpose.namespaceCode());
        asset.setGroupPath(normalizedGroup);
        asset.setAccessLevel(purpose.accessLevel());
        asset.setSourceType(purpose.sourceType());
        asset.setLifecycleStatus(STATUS_AVAILABLE_UNBOUND);
        asset.setCreatorId(request.creatorId());
        // 登记后等待业务绑定事件；超过宽限期仍无有效引用才允许物理清理。
        asset.setCleanupAfter(LocalDateTime.now().plus(deleteGrace));
        asset.setRetryCount(0);
        asset.setDeleted(0);
        try {
            fileAssetMapper.insert(asset);
        } catch (DuplicateKeyException exception) {
            // 同一物理对象只对应一个文件资产；重复接管返回已登记资产。
            FileAsset existing = fileAssetMapper.selectByObjectKey(
                    minioProperties.getBucket(), objectKey);
            if (existing == null) {
                throw exception;
            }
            return toSummary(existing);
        }
        return toSummary(asset);
    }

    /**
     * 判断对象路径是否位于允许接管的交接目录。
     *
     * @param objectKey 对象路径
     * @return 位于交接目录时为 true
     */
    private boolean isHandoverObjectKey(String objectKey) {
        return HANDOVER_PREFIXES.stream().anyMatch(objectKey::startsWith)
                && !objectKey.contains("..");
    }

    @Override
    public FileAssetSummaryDTO summary(Long fileId) {
        return toSummary(requireAsset(fileId));
    }

    @Override
    public FileAccessUrlVO createAccessUrl(Long id) {
        FileAsset asset = requireAsset(id);
        if (!ACCESSIBLE_STATUSES.contains(asset.getLifecycleStatus())) {
            throw new BusinessException(FileErrorCode.FILE_STATUS_INVALID);
        }
        return new FileAccessUrlVO(
                storageService.getUrl(asset.getObjectKey()),
                minioProperties.getExpirySeconds()
        );
    }

    @Override
    public FileAccessUrlVO createAccessUrlByFileId(Long fileId) {
        return createAccessUrl(fileId);
    }

    @Override
    public FileAccessUrlVO createPreviewUrl(Long id) {
        return createAccessUrl(id);
    }

    @Override
    @Transactional
    public void requestDelete(Long id) {
        FileAsset asset = requireAsset(id);
        if (!SOURCE_MANUAL.equals(asset.getSourceType())) {
            throw new BusinessException(FileErrorCode.FILE_NOT_DELETABLE,
                    "历史盘点文件只读，完成业务归属迁移后才能删除");
        }
        if (!STATUS_ACTIVE.equals(asset.getLifecycleStatus())) {
            throw new BusinessException(FileErrorCode.FILE_STATUS_INVALID);
        }
        LocalDateTime requestedAt = LocalDateTime.now();
        int changed = fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, id)
                .eq(FileAsset::getLifecycleStatus, STATUS_ACTIVE)
                .set(FileAsset::getLifecycleStatus, STATUS_PENDING_DELETE)
                .set(FileAsset::getDeleteRequestedAt, requestedAt)
                .set(FileAsset::getCleanupAfter, requestedAt.plus(deleteGrace))
                .set(FileAsset::getLastError, null));
        if (changed == 0) {
            throw new BusinessException(FileErrorCode.FILE_STATUS_INVALID);
        }
    }

    @Override
    public FileReconcileVO reconcile() {
        List<PhysicalObject> physicalObjects = objectInventory.listAll();
        List<FileAsset> knownAssets = fileAssetMapper.selectList(new LambdaQueryWrapper<>());
        Set<String> knownKeys = knownAssets.stream().map(FileAsset::getObjectKey).collect(java.util.stream.Collectors.toSet());
        Set<String> physicalKeys = physicalObjects.stream().map(PhysicalObject::objectKey).collect(java.util.stream.Collectors.toSet());
        long created = 0;
        for (PhysicalObject object : physicalObjects) {
            if (!knownKeys.contains(object.objectKey()) && insertDiscovered(object)) {
                created++;
            }
        }
        long missing = knownAssets.stream().filter(asset -> !physicalKeys.contains(asset.getObjectKey())).count();
        return new FileReconcileVO(
                physicalObjects.size(),
                created,
                physicalObjects.size() - created,
                missing
        );
    }

    @Override
    public int cleanupDueAssets() {
        LocalDateTime now = LocalDateTime.now();
        List<FileAsset> dueAssets = fileAssetMapper.selectList(new LambdaQueryWrapper<FileAsset>()
                .and(scope -> scope
                        .in(FileAsset::getLifecycleStatus, STATUS_PENDING_DELETE, STATUS_DELETE_FAILED)
                        .le(FileAsset::getCleanupAfter, now)
                        .or(unbound -> unbound
                                .eq(FileAsset::getLifecycleStatus, STATUS_AVAILABLE_UNBOUND)
                                .isNotNull(FileAsset::getCleanupAfter)
                                .le(FileAsset::getCleanupAfter, now)))
                .orderByAsc(FileAsset::getCleanupAfter)
                .last("LIMIT 100"));
        int cleaned = 0;
        for (FileAsset asset : dueAssets) {
            // 宽限期内可能重新绑定；物理删除前再确认引用投影为空。
            if (STATUS_AVAILABLE_UNBOUND.equals(asset.getLifecycleStatus())
                    && fileBindingMapper.countActiveByFileId(asset.getId()) > 0) {
                continue;
            }
            try {
                storageService.delete(asset.getObjectKey());
                fileAssetMapper.markPhysicallyDeleted(asset.getId());
                cleaned++;
            } catch (RuntimeException exception) {
                markCleanupFailed(asset, now, exception);
            }
        }
        return cleaned;
    }

    private boolean insertDiscovered(PhysicalObject object) {
        FileAsset asset = new FileAsset();
        asset.setBucketName(minioProperties.getBucket());
        asset.setObjectKey(object.objectKey());
        asset.setOriginalName(extractFileName(object.objectKey()));
        asset.setFileSize(object.size());
        asset.setNamespaceCode("discovered");
        asset.setGroupPath(inferTopLevelGroup(object.objectKey()));
        asset.setSourceType(SOURCE_DISCOVERED);
        asset.setLifecycleStatus(STATUS_DISCOVERED);
        asset.setRetryCount(0);
        asset.setDeleted(0);
        try {
            fileAssetMapper.insert(asset);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private void markCleanupFailed(FileAsset asset, LocalDateTime now, RuntimeException exception) {
        int retries = asset.getRetryCount() == null ? 1 : asset.getRetryCount() + 1;
        fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, asset.getId())
                .set(FileAsset::getLifecycleStatus, STATUS_DELETE_FAILED)
                .set(FileAsset::getRetryCount, retries)
                .set(FileAsset::getCleanupAfter, now.plus(retryDelay))
                .set(FileAsset::getLastError, truncate(exception.getMessage(), 500)));
    }

    private void compensateUploadedObject(String objectKey, RuntimeException originalException) {
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private FileAsset requireAsset(Long id) {
        FileAsset asset = fileAssetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
        }
        return asset;
    }

    private List<FileGroupVO> aggregateGroups(List<FileAsset> assets) {
        Map<String, long[]> groups = new LinkedHashMap<>();
        for (FileAsset asset : assets) {
            String groupPath = asset.getGroupPath() == null ? "" : asset.getGroupPath();
            long[] aggregate = groups.computeIfAbsent(groupPath, ignored -> new long[2]);
            aggregate[0]++;
            aggregate[1] += safeSize(asset.getFileSize());
        }
        List<FileGroupVO> result = new ArrayList<>();
        groups.forEach((path, aggregate) -> result.add(new FileGroupVO(path, aggregate[0], aggregate[1])));
        result.sort(Comparator.comparing(FileGroupVO::groupPath));
        return result;
    }

    private FileAssetVO toVO(FileAsset asset) {
        return FileAssetVO.builder()
                .id(asset.getId())
                .bucketName(asset.getBucketName())
                .objectKey(asset.getObjectKey())
                .originalName(asset.getOriginalName())
                .fileExtension(resolveExtension(asset.getOriginalName()))
                .contentType(asset.getContentType())
                .fileSize(asset.getFileSize())
                .previewType(resolvePreviewType(asset))
                .previewable(isPreviewable(asset))
                .namespaceCode(asset.getNamespaceCode())
                .groupPath(asset.getGroupPath())
                .accessLevel(asset.getAccessLevel())
                .sourceType(asset.getSourceType())
                .lifecycleStatus(asset.getLifecycleStatus())
                .creatorId(asset.getCreatorId())
                .cleanupAfter(asset.getCleanupAfter())
                .createTime(asset.getCreateTime())
                .updateTime(asset.getUpdateTime())
                .lastError(asset.getLastError())
                .deletable(SOURCE_MANUAL.equals(asset.getSourceType())
                        && STATUS_ACTIVE.equals(asset.getLifecycleStatus()))
                .build();
    }

    private FileAssetSummaryDTO toSummary(FileAsset asset) {
        return new FileAssetSummaryDTO(
                asset.getId(),
                asset.getNamespaceCode(),
                asset.getSourceType(),
                asset.getOriginalName(),
                asset.getContentType(),
                asset.getFileSize(),
                asset.getLifecycleStatus()
        );
    }

    private String resolveOriginalName(MultipartFile file, String objectKey) {
        return StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : extractFileName(objectKey);
    }

    private String resolveExtension(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "";
        }
        String name = originalName.trim();
        int separator = name.lastIndexOf('.');
        if (separator <= 0 || separator == name.length() - 1) {
            return "";
        }
        return name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private Long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolvePreviewType(FileAsset asset) {
        String contentType = asset.getContentType();
        String extension = resolveExtension(asset.getOriginalName());
        if (contentType != null && contentType.startsWith("image/")) {
            return "IMAGE";
        }
        if ("application/pdf".equalsIgnoreCase(contentType) || "pdf".equals(extension)) {
            return "PDF";
        }
        if ("text/markdown".equalsIgnoreCase(contentType) || Set.of("md", "markdown").contains(extension)) {
            return "MARKDOWN";
        }
        if ((contentType != null && contentType.startsWith("text/"))
                || Set.of("txt", "csv", "json", "xml", "yaml", "yml", "log").contains(extension)) {
            return "TEXT";
        }
        if ((contentType != null && StorageContentTypes.ARCHIVE.contains(contentType))
                || Set.of("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz").contains(extension)) {
            return "ARCHIVE";
        }
        return "UNSUPPORTED";
    }

    private boolean isPreviewable(FileAsset asset) {
        return Set.of("IMAGE", "PDF", "TEXT", "MARKDOWN").contains(resolvePreviewType(asset));
    }

    private String extractFileName(String objectKey) {
        int separator = objectKey.lastIndexOf('/');
        return separator >= 0 ? objectKey.substring(separator + 1) : objectKey;
    }

    private String inferTopLevelGroup(String objectKey) {
        int separator = objectKey.indexOf('/');
        return separator > 0 ? objectKey.substring(0, separator) : "root";
    }

    private long safeSize(Long size) {
        return size == null ? 0 : size;
    }

    private String truncate(String message, int maxLength) {
        if (!StringUtils.hasText(message)) {
            return "对象存储删除失败";
        }
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
    }
}
