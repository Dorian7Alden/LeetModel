package com.leetmodel.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.leetmodel.file.enums.FileErrorCode;
import com.leetmodel.file.enums.FilePurpose;
import com.leetmodel.file.mapper.FileAssetMapper;
import com.leetmodel.file.mapper.FileUploadSessionMapper;
import com.leetmodel.file.service.FileUploadService;
import com.leetmodel.file.support.FileGroupPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 预签名分片直传会话实现。
 *
 * <p>分片对象以对象存储为事实源：查询与完成时按分片对象实际大小校验，避免维护第二份分片状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {
    private static final String SESSION_UPLOADING = "UPLOADING";
    private static final String SESSION_COMPLETING = "COMPLETING";
    private static final String SESSION_COMPLETED = "COMPLETED";
    private static final String SESSION_ABORTED = "ABORTED";
    private static final String SESSION_EXPIRED = "EXPIRED";
    private static final String ASSET_UPLOADING = "UPLOADING";
    private static final String ASSET_AVAILABLE_UNBOUND = "AVAILABLE_UNBOUND";
    private static final String SESSION_PREFIX = "upload-sessions/";
    private static final int READ_BUFFER_SIZE = 8192;

    private final FileUploadSessionMapper fileUploadSessionMapper;
    private final FileAssetMapper fileAssetMapper;
    private final StorageService storageService;
    private final MinioProperties minioProperties;
    private final FileUploadProperties properties;

    @Value("${file.lifecycle.delete-grace:24h}")
    private Duration deleteGrace;

    @Override
    @Transactional
    public FileUploadSessionDTO create(FileUploadCreateRequestDTO request) {
        FilePurpose purpose = FilePurpose.fromCode(request.purpose());
        if (!purpose.allowsContentType(request.contentType())) {
            throw new BusinessException(FileErrorCode.FILE_CONTENT_TYPE_NOT_ALLOWED,
                    "用途 " + purpose.name() + " 不允许类型 " + request.contentType());
        }
        BusinessException.throwIf(request.fileSize() > properties.getMaxFileSize(),
                FileErrorCode.UPLOAD_SIZE_EXCEEDED);
        long partSize = resolvePartSize(request.partSize());
        int partCount = (int) ((request.fileSize() + partSize - 1) / partSize);
        BusinessException.throwIf(partCount <= 0 || partCount > properties.getMaxPartCount(),
                FileErrorCode.UPLOAD_PART_INDEX_INVALID);

        String normalizedGroup = FileGroupPath.normalize(request.groupPath());
        String token = UUID.randomUUID().toString();
        String prefix = normalizedGroup.isEmpty()
                ? purpose.namespaceCode()
                : purpose.namespaceCode() + "/" + normalizedGroup;
        String objectKey = prefix + "/" + token + resolveExtension(request.originalName());

        FileAsset asset = new FileAsset();
        asset.setBucketName(minioProperties.getBucket());
        asset.setObjectKey(objectKey);
        asset.setOriginalName(request.originalName());
        asset.setContentType(request.contentType());
        asset.setFileSize(request.fileSize());
        asset.setNamespaceCode(purpose.namespaceCode());
        asset.setGroupPath(normalizedGroup);
        asset.setAccessLevel(purpose.accessLevel());
        asset.setSourceType(purpose.sourceType());
        asset.setLifecycleStatus(ASSET_UPLOADING);
        asset.setCreatorId(request.creatorId());
        asset.setRetryCount(0);
        asset.setDeleted(0);
        fileAssetMapper.insert(asset);

        FileUploadSession session = new FileUploadSession();
        session.setSessionToken(token);
        session.setFileId(asset.getId());
        session.setPurposeCode(purpose.name());
        session.setObjectKey(objectKey);
        session.setPartPrefix(SESSION_PREFIX + token + "/part-");
        session.setOriginalName(request.originalName());
        session.setContentType(request.contentType());
        session.setFileSize(request.fileSize());
        session.setPartSize(partSize);
        session.setPartCount(partCount);
        session.setStatus(SESSION_UPLOADING);
        session.setCreatorId(request.creatorId());
        session.setExpireTime(LocalDateTime.now().plus(properties.getSessionExpiry()));
        session.setDeleted(0);
        fileUploadSessionMapper.insert(session);

        return toDTO(session, List.of());
    }

    @Override
    public FileUploadSessionDTO status(String sessionId) {
        FileUploadSession session = requireSession(sessionId);
        return toDTO(session, uploadedParts(session));
    }

    @Override
    public FileUploadPartUrlDTO partUrl(String sessionId, Integer partNumber) {
        FileUploadSession session = requireActiveSession(sessionId);
        BusinessException.throwIf(partNumber == null || partNumber < 1 || partNumber > session.getPartCount(),
                FileErrorCode.UPLOAD_PART_INDEX_INVALID);
        String uploadUrl = storageService.getUploadUrl(partKey(session, partNumber));
        return new FileUploadPartUrlDTO(partNumber, uploadUrl, minioProperties.getUploadExpirySeconds());
    }

    @Override
    @Transactional
    public FileAssetSummaryDTO complete(String sessionId) {
        FileUploadSession session = requireActiveSession(sessionId);
        BusinessException.throwIf(
                fileUploadSessionMapper.claim(session.getId(), SESSION_COMPLETING) != 1,
                FileErrorCode.UPLOAD_SESSION_STATE_INVALID);

        List<String> partKeys = expectedPartKeys(session);
        try {
            verifyParts(session, partKeys);
            storageService.composeObjects(session.getObjectKey(), partKeys);
            long actualSize = storageService.sizeOf(session.getObjectKey());
            BusinessException.throwIf(actualSize != session.getFileSize(),
                    FileErrorCode.FILE_SIZE_MISMATCH);
            verifyContentDigest(session);
        } catch (RuntimeException exception) {
            rollbackFailedCompletion(session);
            throw exception;
        }

        fileAssetMapper.update(null, new LambdaUpdateWrapper<FileAsset>()
                .eq(FileAsset::getId, session.getFileId())
                .set(FileAsset::getLifecycleStatus, ASSET_AVAILABLE_UNBOUND)
                .set(FileAsset::getFileSize, session.getFileSize())
                .set(FileAsset::getCleanupAfter, LocalDateTime.now().plus(deleteGrace)));
        fileUploadSessionMapper.finish(session.getId(), SESSION_COMPLETED, 1);
        deleteObjectsQuietly(partKeys);

        FileAsset asset = fileAssetMapper.selectById(session.getFileId());
        return new FileAssetSummaryDTO(asset.getId(), asset.getNamespaceCode(), asset.getSourceType(),
                asset.getOriginalName(), asset.getContentType(), asset.getFileSize(),
                asset.getLifecycleStatus());
    }

    @Override
    @Transactional
    public void abort(String sessionId) {
        FileUploadSession session = requireSession(sessionId);
        if (!SESSION_UPLOADING.equals(session.getStatus())) {
            return;
        }
        deleteObjectsQuietly(expectedPartKeys(session));
        fileUploadSessionMapper.finish(session.getId(), SESSION_ABORTED, 0);
        fileAssetMapper.markPhysicallyDeleted(session.getFileId());
    }

    @Override
    public int cleanupExpiredSessions() {
        List<FileUploadSession> expired = fileUploadSessionMapper.selectList(
                new LambdaQueryWrapper<FileUploadSession>()
                        .eq(FileUploadSession::getStatus, SESSION_UPLOADING)
                        .le(FileUploadSession::getExpireTime, LocalDateTime.now())
                        .orderByAsc(FileUploadSession::getExpireTime)
                        .last("LIMIT 50"));
        int handled = 0;
        for (FileUploadSession session : expired) {
            try {
                deleteObjectsQuietly(expectedPartKeys(session));
                fileUploadSessionMapper.finish(session.getId(), SESSION_EXPIRED, 0);
                fileAssetMapper.markPhysicallyDeleted(session.getFileId());
                handled++;
            } catch (RuntimeException exception) {
                log.warn("过期上传会话清理失败: sessionId={}, exceptionType={}",
                        session.getSessionToken(), exception.getClass().getSimpleName());
            }
        }
        return handled;
    }

    // ==================== 私有方法 ====================

    private long resolvePartSize(Long requestedPartSize) {
        long partSize = requestedPartSize == null ? properties.getDefaultPartSize() : requestedPartSize;
        return Math.max(partSize, properties.getMinPartSize());
    }

    private FileUploadSession requireSession(String sessionId) {
        FileUploadSession session = fileUploadSessionMapper.selectOne(
                new LambdaQueryWrapper<FileUploadSession>()
                        .eq(FileUploadSession::getSessionToken, sessionId)
                        .last("LIMIT 1"));
        BusinessException.throwIf(session == null, FileErrorCode.UPLOAD_SESSION_NOT_FOUND);
        return session;
    }

    private FileUploadSession requireActiveSession(String sessionId) {
        FileUploadSession session = requireSession(sessionId);
        BusinessException.throwIf(!SESSION_UPLOADING.equals(session.getStatus()),
                FileErrorCode.UPLOAD_SESSION_STATE_INVALID);
        BusinessException.throwIf(session.getExpireTime().isBefore(LocalDateTime.now()),
                FileErrorCode.UPLOAD_SESSION_STATE_INVALID);
        return session;
    }

    private String partKey(FileUploadSession session, int partNumber) {
        return session.getPartPrefix() + partNumber;
    }

    private List<String> expectedPartKeys(FileUploadSession session) {
        List<String> keys = new ArrayList<>(session.getPartCount());
        for (int partNumber = 1; partNumber <= session.getPartCount(); partNumber++) {
            keys.add(partKey(session, partNumber));
        }
        return keys;
    }

    private List<Integer> uploadedParts(FileUploadSession session) {
        List<Integer> uploaded = new ArrayList<>();
        for (int partNumber = 1; partNumber <= session.getPartCount(); partNumber++) {
            if (existingSize(partKey(session, partNumber)) == expectedPartSize(session, partNumber)) {
                uploaded.add(partNumber);
            }
        }
        return uploaded;
    }

    private void verifyParts(FileUploadSession session, List<String> partKeys) {
        for (int index = 0; index < partKeys.size(); index++) {
            long expected = expectedPartSize(session, index + 1);
            if (existingSize(partKeys.get(index)) != expected) {
                throw new BusinessException(FileErrorCode.UPLOAD_PART_INCOMPLETE,
                        "分片 " + (index + 1) + " 未上传或大小不一致");
            }
        }
    }

    private long expectedPartSize(FileUploadSession session, int partNumber) {
        if (partNumber < session.getPartCount()) {
            return session.getPartSize();
        }
        return session.getFileSize() - session.getPartSize() * (session.getPartCount() - 1L);
    }

    private long existingSize(String objectKey) {
        try {
            return storageService.sizeOf(objectKey);
        } catch (RuntimeException exception) {
            // 对象尚未上传时按缺失处理，由调用方给出明确业务错误
            return -1L;
        }
    }

    private void verifyContentDigest(FileUploadSession session) {
        if (session.getFileSize() > properties.getSha256VerifyMaxBytes()) {
            log.info("文件超过摘要复核阈值，跳过合并内容摘要校验: sessionId={}, fileSize={}",
                    session.getSessionToken(), session.getFileSize());
            return;
        }
        String actual = sha256(session.getObjectKey());
        log.info("合并对象摘要计算完成: sessionId={}, sha256={}", session.getSessionToken(), actual);
    }

    private String sha256(String objectKey) {
        try (InputStream input = storageService.download(objectKey)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.io.IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(FileErrorCode.STORAGE_UNAVAILABLE, "合并对象摘要计算失败");
        }
    }

    private void rollbackFailedCompletion(FileUploadSession session) {
        fileUploadSessionMapper.claim(session.getId(), SESSION_UPLOADING);
        deleteObjectsQuietly(List.of(session.getObjectKey()));
    }

    private void deleteObjectsQuietly(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                storageService.delete(objectKey);
            } catch (RuntimeException exception) {
                log.warn("分片对象清理失败: objectKey={}, exceptionType={}",
                        objectKey, exception.getClass().getSimpleName());
            }
        }
    }

    private String resolveExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int separator = originalName.lastIndexOf('.');
        if (separator <= 0 || separator == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(separator).toLowerCase(Locale.ROOT);
    }

    private FileUploadSessionDTO toDTO(FileUploadSession session, List<Integer> uploadedParts) {
        return new FileUploadSessionDTO(
                session.getSessionToken(),
                session.getFileId(),
                session.getOriginalName(),
                session.getFileSize(),
                session.getPartSize(),
                session.getPartCount(),
                session.getStatus(),
                uploadedParts,
                session.getExpireTime());
    }
}
