package com.leetmodel.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.common.core.config.MinioProperties;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.storage.StorageContentTypes;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.file.entity.FileAsset;
import com.leetmodel.file.enums.FileErrorCode;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.model.FileAccessUrlVO;
import com.leetmodel.file.model.FileAssetPageVO;
import com.leetmodel.file.model.FileAssetQuery;
import com.leetmodel.file.model.FileAssetVO;
import com.leetmodel.file.model.FileGroupVO;
import com.leetmodel.file.model.FileReconcileVO;
import com.leetmodel.file.service.FileAssetService;
import com.leetmodel.file.storage.FileObjectInventory;
import com.leetmodel.file.storage.PhysicalObject;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FileAssetServiceImpl implements FileAssetService {
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_DISCOVERED = "DISCOVERED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISCOVERED = "DISCOVERED";
    private static final String STATUS_PENDING_DELETE = "PENDING_DELETE";
    private static final String STATUS_DELETE_FAILED = "DELETE_FAILED";
    private static final String UNGROUPED_FILTER = "__ungrouped__";
    private static final Pattern GROUP_PATH_PATTERN = Pattern.compile("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*");

    private final FileAssetMapper fileAssetMapper;
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
        String normalizedGroup = normalizeGroupPath(groupPath);
        String prefix = normalizedGroup.isEmpty() ? "manual" : "manual/" + normalizedGroup;
        String objectKey = storageService.upload(file, prefix, StorageContentTypes.ARCHIVE);
        FileAsset asset = new FileAsset();
        asset.setBucketName(minioProperties.getBucket());
        asset.setObjectKey(objectKey);
        asset.setOriginalName(resolveOriginalName(file, objectKey));
        asset.setContentType(file.getContentType());
        asset.setFileSize(file.getSize());
        asset.setNamespaceCode("manual");
        asset.setGroupPath(normalizedGroup);
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
    public FileAccessUrlVO createAccessUrl(Long id) {
        FileAsset asset = requireAsset(id);
        if (!Set.of(STATUS_ACTIVE, STATUS_DISCOVERED).contains(asset.getLifecycleStatus())) {
            throw new BusinessException(FileErrorCode.FILE_STATUS_INVALID);
        }
        return new FileAccessUrlVO(
                storageService.getUrl(asset.getObjectKey()),
                minioProperties.getExpirySeconds()
        );
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
                .in(FileAsset::getLifecycleStatus, STATUS_PENDING_DELETE, STATUS_DELETE_FAILED)
                .le(FileAsset::getCleanupAfter, now)
                .orderByAsc(FileAsset::getCleanupAfter)
                .last("LIMIT 100"));
        int cleaned = 0;
        for (FileAsset asset : dueAssets) {
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

    private String normalizeGroupPath(String groupPath) {
        if (!StringUtils.hasText(groupPath)) {
            return "";
        }
        String normalized = groupPath.trim();
        if (normalized.length() > 128 || !GROUP_PATH_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(FileErrorCode.GROUP_PATH_INVALID,
                    "分组仅支持字母、数字、/、_、-，且不能以 / 开头或结尾");
        }
        return normalized;
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
