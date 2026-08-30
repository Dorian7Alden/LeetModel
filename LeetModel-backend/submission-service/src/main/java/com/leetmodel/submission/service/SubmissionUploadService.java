package com.leetmodel.submission.service;

import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.submission.config.SubmissionUploadProperties;
import com.leetmodel.submission.dto.UploadInitializeRequest;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionUpload;
import com.leetmodel.submission.entity.SubmissionUploadChunk;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionUploadChunkMapper;
import com.leetmodel.submission.mapper.SubmissionUploadMapper;
import com.leetmodel.submission.storage.SubmissionChunkStorage;
import com.leetmodel.submission.vo.SubmissionVO;
import com.leetmodel.submission.vo.UploadSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 论文 PDF 分片断点续传服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionUploadService {
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_COMPLETING = "COMPLETING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_ABORTED = "ABORTED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    private final SubmissionUploadMapper uploadMapper;
    private final SubmissionUploadChunkMapper chunkMapper;
    private final SubmissionUploadPersistenceService persistenceService;
    private final SubmissionService submissionService;
    private final TeamFeignClient teamFeignClient;
    private final StorageService storageService;
    private final SubmissionChunkStorage chunkStorage;
    private final SubmissionUploadProperties properties;

    /**
     * 初始化或恢复论文上传会话。
     * @param request 文件元数据
     * @param userId 当前用户 ID
     * @return 上传会话
     */
    public UploadSessionVO initialize(UploadInitializeRequest request, Long userId) {
        // 校验文件和队伍提交资格
        validateMetadata(request);
        TeamSubmissionAccessDTO access = requiredSubmissionAccess(request.getTeamId(), userId);
        validateWindow(access);

        // 恢复同一文件，或用新文件替换本人尚未完成的会话
        SubmissionUpload active = uploadMapper.selectActiveByTeamId(request.getTeamId());
        if (active != null) {
            if (isExpired(active) && canExpire(active)) expireAndCleanup(active);
            else if (!active.getUploaderId().equals(userId)) {
                throw new BusinessException(SubmissionErrorCode.UPLOAD_ALREADY_ACTIVE);
            } else if (matches(active, request)) {
                return toVO(active);
            } else if (STATUS_COMPLETING.equals(active.getStatus())) {
                throw new BusinessException(SubmissionErrorCode.UPLOAD_COMPLETING);
            } else {
                abortAndCleanup(active);
            }
        }

        // 创建新的活动上传会话
        SubmissionUpload upload = buildUpload(request, access, userId);
        try {
            uploadMapper.insert(upload);
        } catch (DuplicateKeyException exception) {
            SubmissionUpload concurrent = uploadMapper.selectActiveByTeamId(request.getTeamId());
            if (concurrent != null && concurrent.getUploaderId().equals(userId) && matches(concurrent, request)) {
                return toVO(concurrent);
            }
            throw new BusinessException(SubmissionErrorCode.UPLOAD_ALREADY_ACTIVE);
        }
        return toVO(upload);
    }

    /**
     * 查询上传会话和已接收分片。
     * @param uploadToken 上传会话标识
     * @param userId 当前用户 ID
     * @return 上传会话
     */
    public UploadSessionVO get(String uploadToken, Long userId) {
        SubmissionUpload upload = requiredOwnedUpload(uploadToken, userId);
        if (isExpired(upload) && canExpire(upload)) {
            expireAndCleanup(upload);
            upload.setStatus(STATUS_EXPIRED);
            upload.setActiveMarker(null);
        }
        return toVO(upload);
    }

    /**
     * 幂等上传一个 PDF 分片。
     * @param uploadToken 上传会话标识
     * @param chunkIndex 从 0 开始的分片序号
     * @param chunkSha256 分片 SHA-256
     * @param file 分片内容
     * @param userId 当前用户 ID
     * @return 更新后的上传会话
     */
    public UploadSessionVO uploadChunk(String uploadToken, Integer chunkIndex, String chunkSha256,
                                       MultipartFile file, Long userId) {
        // 校验会话、序号、大小和分片摘要
        SubmissionUpload upload = requiredOwnedUpload(uploadToken, userId);
        validateUploadAvailable(upload);
        validateChunk(upload, chunkIndex, chunkSha256, file);
        String normalizedSha256 = chunkSha256.toLowerCase(Locale.ROOT);
        String actualSha256 = sha256(file);
        BusinessException.throwIf(
                !normalizedSha256.equals(actualSha256),
                SubmissionErrorCode.CHUNK_CHECKSUM_MISMATCH
        );

        // 重复上传内容一致时直接返回，内容不一致时拒绝覆盖
        SubmissionUploadChunk existing = chunkMapper.selectByUploadAndIndex(upload.getId(), chunkIndex);
        if (existing != null) {
            validateExistingChunk(existing, file.getSize(), actualSha256);
            return toVO(upload);
        }

        // 对象名包含摘要，避免并发的不同内容互相覆盖
        String objectName = "submission-uploads/" + uploadToken + "/"
                + chunkIndex + "-" + actualSha256;
        chunkStorage.upload(objectName, file);
        SubmissionUploadChunk chunk = new SubmissionUploadChunk();
        chunk.setUploadId(upload.getId());
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkSize(file.getSize());
        chunk.setChunkSha256(actualSha256);
        chunk.setObjectName(objectName);
        try {
            chunkMapper.insert(chunk);
        } catch (DuplicateKeyException exception) {
            SubmissionUploadChunk concurrent = chunkMapper.selectByUploadAndIndex(upload.getId(), chunkIndex);
            if (concurrent != null) {
                if (concurrent.getChunkSize() != file.getSize()
                        || !concurrent.getChunkSha256().equals(actualSha256)) {
                    chunkStorage.delete(List.of(objectName));
                    throw new BusinessException(SubmissionErrorCode.CHUNK_CONFLICT);
                }
                return toVO(upload);
            }
            chunkStorage.delete(List.of(objectName));
            throw exception;
        } catch (RuntimeException exception) {
            chunkStorage.delete(List.of(objectName));
            throw exception;
        }
        return toVO(upload);
    }

    /**
     * 合并分片并幂等创建提交版本。
     * @param uploadToken 上传会话标识
     * @param userId 当前用户 ID
     * @return 提交版本
     */
    public SubmissionVO complete(String uploadToken, Long userId) {
        SubmissionUpload upload = requiredOwnedUpload(uploadToken, userId);

        // 已建立提交关联时只需幂等补偿评审触发
        if (upload.getSubmissionId() != null) return finishReviewAndUpload(upload);
        validateUploadAvailableForCompletion(upload);
        TeamSubmissionAccessDTO access = requiredSubmissionAccess(upload.getTeamId(), userId);
        validateWindow(access);
        List<SubmissionUploadChunk> chunks = requiredCompleteChunks(upload);

        // 原子抢占合并权，只允许接管超时的合并任务
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(properties.getCompletionStaleSeconds());
        int claimed = uploadMapper.claimCompletion(upload.getId(), now, staleBefore);
        if (claimed == 0) {
            SubmissionUpload latest = requiredOwnedUpload(uploadToken, userId);
            if (latest.getSubmissionId() != null) return finishReviewAndUpload(latest);
            BusinessException.throwIf(
                    STATUS_COMPLETING.equals(latest.getStatus()),
                    SubmissionErrorCode.UPLOAD_COMPLETING
            );
            throw new BusinessException(SubmissionErrorCode.UPLOAD_STATE_INVALID);
        }

        // 在数据库事务外完成 MinIO 合并和最终 PDF 校验
        try {
            List<String> sourceObjects = chunks.stream()
                    .map(SubmissionUploadChunk::getObjectName)
                    .toList();
            chunkStorage.compose(upload.getFinalObjectName(), sourceObjects);
            validateMergedPdf(upload);
        } catch (RuntimeException exception) {
            uploadMapper.resetCompletion(upload.getId());
            deleteFinalObject(upload.getFinalObjectName());
            throw exception;
        }

        // 短事务创建提交，再幂等触发评审
        Submission submission = persistenceService.createSubmission(upload.getId());
        SubmissionVO response = submissionService.triggerReview(submission);
        uploadMapper.markCompleted(upload.getId(), submission.getId());
        cleanupChunks(upload.getId(), chunks);
        return response;
    }

    /**
     * 取消尚未进入合并的上传会话。
     * @param uploadToken 上传会话标识
     * @param userId 当前用户 ID
     */
    public void cancel(String uploadToken, Long userId) {
        SubmissionUpload upload = requiredOwnedUpload(uploadToken, userId);
        if (STATUS_ABORTED.equals(upload.getStatus()) || STATUS_EXPIRED.equals(upload.getStatus())) return;
        BusinessException.throwIf(
                !STATUS_UPLOADING.equals(upload.getStatus()),
                SubmissionErrorCode.UPLOAD_STATE_INVALID
        );
        abortAndCleanup(upload);
    }

    /**
     * 定时清理过期的未完成上传会话。
     */
    @Scheduled(fixedDelayString = "${submission.upload.cleanup-delay-ms:3600000}")
    public void cleanupExpiredUploads() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(properties.getCompletionStaleSeconds());
        List<SubmissionUpload> expiredUploads = uploadMapper.selectExpired(now, staleBefore);
        for (SubmissionUpload upload : expiredUploads) expireAndCleanup(upload);
    }

    /**
     * 构建新上传会话。
     * @param request 初始化请求
     * @param access 队伍提交权限快照
     * @param userId 上传者 ID
     * @return 上传会话
     */
    private SubmissionUpload buildUpload(UploadInitializeRequest request,
                                         TeamSubmissionAccessDTO access,
                                         Long userId) {
        String uploadToken = UUID.randomUUID().toString();
        long chunkSize = properties.getChunkSize();
        int totalChunks = Math.toIntExact((request.getFileSize() + chunkSize - 1) / chunkSize);
        SubmissionUpload upload = new SubmissionUpload();
        upload.setUploadToken(uploadToken);
        upload.setTeamId(request.getTeamId());
        upload.setProblemId(access.getProblemId());
        upload.setUploaderId(userId);
        upload.setOriginalFilename(request.getOriginalFilename().trim());
        upload.setFileSize(request.getFileSize());
        upload.setFileSha256(request.getFileSha256().toLowerCase(Locale.ROOT));
        upload.setChunkSize(chunkSize);
        upload.setTotalChunks(totalChunks);
        upload.setStatus(STATUS_UPLOADING);
        upload.setActiveMarker(1);
        upload.setFinalObjectName("submissions/" + request.getTeamId() + "/" + uploadToken + ".pdf");
        upload.setExpiresAt(LocalDateTime.now().plusHours(properties.getSessionExpiryHours()));
        return upload;
    }

    /**
     * 校验初始化文件元数据。
     * @param request 初始化请求
     */
    private void validateMetadata(UploadInitializeRequest request) {
        BusinessException.throwIf(
                request.getFileSize() > properties.getMaxFileSize(),
                SubmissionErrorCode.PDF_SIZE_EXCEEDED
        );
        BusinessException.throwIf(
                !request.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf"),
                SubmissionErrorCode.PDF_ONLY
        );
        BusinessException.throwIf(
                properties.getChunkSize() <= 0 || properties.getChunkSize() > properties.getMaxFileSize(),
                ErrorCodeEnum.SYSTEM_ERROR
        );
    }

    /**
     * 校验分片参数。
     * @param upload 上传会话
     * @param chunkIndex 分片序号
     * @param chunkSha256 分片摘要
     * @param file 分片文件
     */
    private void validateChunk(SubmissionUpload upload, Integer chunkIndex,
                               String chunkSha256, MultipartFile file) {
        BusinessException.throwIf(
                chunkIndex == null || chunkIndex < 0 || chunkIndex >= upload.getTotalChunks(),
                SubmissionErrorCode.CHUNK_INDEX_INVALID
        );
        BusinessException.throwIf(
                chunkSha256 == null || !chunkSha256.matches("^[0-9a-fA-F]{64}$"),
                SubmissionErrorCode.CHUNK_CHECKSUM_MISMATCH
        );
        long expectedSize = expectedChunkSize(upload, chunkIndex);
        BusinessException.throwIf(
                file == null || file.isEmpty() || file.getSize() != expectedSize,
                SubmissionErrorCode.CHUNK_SIZE_INVALID
        );
    }

    /**
     * 获取指定分片的预期大小。
     * @param upload 上传会话
     * @param chunkIndex 分片序号
     * @return 预期字节数
     */
    private long expectedChunkSize(SubmissionUpload upload, int chunkIndex) {
        if (chunkIndex < upload.getTotalChunks() - 1) return upload.getChunkSize();
        return upload.getFileSize() - upload.getChunkSize() * (upload.getTotalChunks() - 1L);
    }

    /**
     * 校验重复分片内容是否一致。
     * @param existing 已有分片
     * @param size 新分片大小
     * @param sha256 新分片摘要
     */
    private void validateExistingChunk(SubmissionUploadChunk existing, long size, String sha256) {
        BusinessException.throwIf(
                existing.getChunkSize() != size || !existing.getChunkSha256().equals(sha256),
                SubmissionErrorCode.CHUNK_CONFLICT
        );
    }

    /**
     * 校验并返回完整的有序分片。
     * @param upload 上传会话
     * @return 有序分片列表
     */
    private List<SubmissionUploadChunk> requiredCompleteChunks(SubmissionUpload upload) {
        List<SubmissionUploadChunk> chunks = chunkMapper.selectByUploadId(upload.getId());
        BusinessException.throwIf(
                chunks.size() != upload.getTotalChunks(),
                SubmissionErrorCode.CHUNK_MISSING
        );
        for (int index = 0; index < chunks.size(); index++) {
            SubmissionUploadChunk chunk = chunks.get(index);
            BusinessException.throwIf(
                    chunk.getChunkIndex() != index
                            || chunk.getChunkSize() != expectedChunkSize(upload, index),
                    SubmissionErrorCode.CHUNK_MISSING
            );
        }
        return chunks;
    }

    /**
     * 校验合并后 PDF 的大小、文件头和 SHA-256。
     * @param upload 上传会话
     */
    private void validateMergedPdf(SubmissionUpload upload) {
        try (InputStream inputStream = storageService.download(upload.getFinalObjectName())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] header = new byte[5];
            int headerLength = 0;
            long totalSize = 0;
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                if (headerLength < header.length) {
                    int copied = Math.min(length, header.length - headerLength);
                    System.arraycopy(buffer, 0, header, headerLength, copied);
                    headerLength += copied;
                }
                digest.update(buffer, 0, length);
                totalSize += length;
            }
            BusinessException.throwIf(
                    headerLength != 5 || !"%PDF-".equals(new String(header, StandardCharsets.US_ASCII)),
                    SubmissionErrorCode.PDF_ONLY
            );
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            BusinessException.throwIf(
                    totalSize != upload.getFileSize() || !actualSha256.equals(upload.getFileSha256()),
                    SubmissionErrorCode.FILE_CHECKSUM_MISMATCH
            );
        } catch (IOException | NoSuchAlgorithmException exception) {
            log.error("校验合并 PDF 失败: {}", upload.getUploadToken(), exception);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 计算上传分片的 SHA-256。
     * @param file 分片文件
     * @return 小写十六进制摘要
     */
    private String sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) digest.update(buffer, 0, length);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            log.error("计算论文分片摘要失败", exception);
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取当前用户拥有的上传会话。
     * @param uploadToken 上传会话标识
     * @param userId 用户 ID
     * @return 上传会话
     */
    private SubmissionUpload requiredOwnedUpload(String uploadToken, Long userId) {
        SubmissionUpload upload = uploadMapper.selectByToken(uploadToken);
        BusinessException.throwIf(
                upload == null || !upload.getUploaderId().equals(userId),
                SubmissionErrorCode.UPLOAD_NOT_FOUND
        );
        return upload;
    }

    /**
     * 获取队伍提交权限快照。
     * @param teamId 队伍 ID
     * @param userId 用户 ID
     * @return 提交权限快照
     */
    private TeamSubmissionAccessDTO requiredSubmissionAccess(Long teamId, Long userId) {
        Result<TeamSubmissionAccessDTO> result = teamFeignClient.getSubmissionAccess(teamId, userId);
        BusinessException.throwIf(
                result == null || !result.isSuccess() || result.getData() == null,
                SubmissionErrorCode.TEAM_NOT_AVAILABLE
        );
        BusinessException.throwIf(
                !Boolean.TRUE.equals(result.getData().getMember()),
                SubmissionErrorCode.NOT_TEAM_MEMBER
        );
        BusinessException.throwIf(
                !Boolean.TRUE.equals(result.getData().getCanSubmit()),
                SubmissionErrorCode.SUBMISSION_PERMISSION_DENIED
        );
        return result.getData();
    }

    /**
     * 校验队伍练习和截止时间。
     * @param access 提交权限快照
     */
    private void validateWindow(TeamSubmissionAccessDTO access) {
        BusinessException.throwIf(
                !"IN_PROGRESS".equals(access.getPracticeStatus()),
                SubmissionErrorCode.PRACTICE_NOT_STARTED
        );
        BusinessException.throwIf(
                access.getDeadlineAt() == null || !LocalDateTime.now().isBefore(access.getDeadlineAt()),
                SubmissionErrorCode.DEADLINE_PASSED
        );
    }

    /**
     * 校验会话可以继续上传分片。
     * @param upload 上传会话
     */
    private void validateUploadAvailable(SubmissionUpload upload) {
        if (isExpired(upload)) {
            expireAndCleanup(upload);
            throw new BusinessException(SubmissionErrorCode.UPLOAD_EXPIRED);
        }
        BusinessException.throwIf(
                !STATUS_UPLOADING.equals(upload.getStatus()),
                SubmissionErrorCode.UPLOAD_STATE_INVALID
        );
    }

    /**
     * 校验会话可以进入合并。
     * @param upload 上传会话
     */
    private void validateUploadAvailableForCompletion(SubmissionUpload upload) {
        if (isExpired(upload) && canExpire(upload)) {
            expireAndCleanup(upload);
            throw new BusinessException(SubmissionErrorCode.UPLOAD_EXPIRED);
        }
        BusinessException.throwIf(
                STATUS_COMPLETED.equals(upload.getStatus()),
                SubmissionErrorCode.SUBMISSION_NOT_FOUND
        );
        BusinessException.throwIf(
                !STATUS_UPLOADING.equals(upload.getStatus())
                        && !STATUS_COMPLETING.equals(upload.getStatus()),
                SubmissionErrorCode.UPLOAD_STATE_INVALID
        );
    }

    /**
     * 补偿触发评审并完成上传会话。
     * @param upload 已关联提交的上传会话
     * @return 提交版本
     */
    private SubmissionVO finishReviewAndUpload(SubmissionUpload upload) {
        Submission submission = submissionService.getSubmission(upload.getSubmissionId());
        SubmissionVO response = submissionService.triggerReview(submission);
        uploadMapper.markCompleted(upload.getId(), submission.getId());
        cleanupChunks(upload.getId(), chunkMapper.selectByUploadId(upload.getId()));
        return response;
    }

    /**
     * 将上传会话转换为前端响应。
     * @param upload 上传会话
     * @return 会话响应
     */
    private UploadSessionVO toVO(SubmissionUpload upload) {
        List<Integer> uploadedChunks = chunkMapper.selectByUploadId(upload.getId()).stream()
                .map(SubmissionUploadChunk::getChunkIndex)
                .toList();
        return UploadSessionVO.builder()
                .uploadId(upload.getUploadToken())
                .teamId(upload.getTeamId())
                .originalFilename(upload.getOriginalFilename())
                .fileSize(upload.getFileSize())
                .chunkSize(upload.getChunkSize())
                .totalChunks(upload.getTotalChunks())
                .uploadedChunks(uploadedChunks)
                .status(upload.getStatus())
                .expiresAt(upload.getExpiresAt())
                .submissionId(upload.getSubmissionId())
                .build();
    }

    /**
     * 判断初始化请求是否对应同一文件。
     * @param upload 已有会话
     * @param request 新初始化请求
     * @return 是否一致
     */
    private boolean matches(SubmissionUpload upload, UploadInitializeRequest request) {
        return upload.getFileSize().equals(request.getFileSize())
                && upload.getOriginalFilename().equals(request.getOriginalFilename().trim())
                && upload.getFileSha256().equalsIgnoreCase(request.getFileSha256());
    }

    /**
     * 判断会话是否已过期。
     * @param upload 上传会话
     * @return 是否过期
     */
    private boolean isExpired(SubmissionUpload upload) {
        return isActive(upload) && !LocalDateTime.now().isBefore(upload.getExpiresAt());
    }

    /**
     * 判断会话是否仍占用队伍上传名额。
     * @param upload 上传会话
     * @return 是否活动
     */
    private boolean isActive(SubmissionUpload upload) {
        return upload.getActiveMarker() != null && upload.getActiveMarker() == 1;
    }

    /**
     * 判断过期会话是否可安全终止，不中断仍在正常合并的请求。
     * @param upload 上传会话
     * @return 是否可过期
     */
    private boolean canExpire(SubmissionUpload upload) {
        if (STATUS_UPLOADING.equals(upload.getStatus())) return true;
        if (!STATUS_COMPLETING.equals(upload.getStatus()) || upload.getCompletingAt() == null) return false;
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusSeconds(properties.getCompletionStaleSeconds());
        return upload.getCompletingAt().isBefore(staleBefore);
    }

    /**
     * 终止会话并清理分片。
     * @param upload 上传会话
     */
    private void abortAndCleanup(SubmissionUpload upload) {
        if (uploadMapper.markTerminal(upload.getId(), upload.getStatus(), STATUS_ABORTED) == 1) {
            cleanupChunks(upload.getId(), chunkMapper.selectByUploadId(upload.getId()));
        }
    }

    /**
     * 过期会话并清理分片。
     * @param upload 上传会话
     */
    private void expireAndCleanup(SubmissionUpload upload) {
        if (uploadMapper.markTerminal(upload.getId(), upload.getStatus(), STATUS_EXPIRED) == 1) {
            cleanupChunks(upload.getId(), chunkMapper.selectByUploadId(upload.getId()));
        }
    }

    /**
     * 删除分片对象和分片元数据。
     * @param uploadId 上传会话内部 ID
     * @param chunks 分片列表
     */
    private void cleanupChunks(Long uploadId, List<SubmissionUploadChunk> chunks) {
        if (chunks.isEmpty()) return;
        chunkStorage.delete(chunks.stream().map(SubmissionUploadChunk::getObjectName).toList());
        chunkMapper.deleteByUploadId(uploadId);
    }

    /**
     * 尝试删除无效的最终对象。
     * @param objectName 对象路径
     */
    private void deleteFinalObject(String objectName) {
        try {
            storageService.delete(objectName);
        } catch (RuntimeException cleanupException) {
            log.error("删除无效合并 PDF 失败: {}", objectName, cleanupException);
        }
    }
}
