package com.leetmodel.submission.service;

import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionUploadServiceTest {
    @Mock SubmissionUploadMapper uploadMapper;
    @Mock SubmissionUploadChunkMapper chunkMapper;
    @Mock SubmissionUploadPersistenceService persistenceService;
    @Mock SubmissionService submissionService;
    @Mock TeamFeignClient teamFeignClient;
    @Mock StorageService storageService;
    @Mock SubmissionChunkStorage chunkStorage;

    private SubmissionUploadService service;
    private SubmissionUploadProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SubmissionUploadProperties();
        properties.setChunkSize(5L * 1024 * 1024);
        service = new SubmissionUploadService(
                uploadMapper,
                chunkMapper,
                persistenceService,
                submissionService,
                teamFeignClient,
                storageService,
                chunkStorage,
                properties
        );
    }

    @Test
    void rejectPdfLargerThanTwentyMegabytesBeforeCreatingSession() {
        UploadInitializeRequest request = request(20L * 1024 * 1024 + 1);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.initialize(request, 10L)
        );

        assertEquals(SubmissionErrorCode.PDF_SIZE_EXCEEDED.getCode(), error.getCode());
        verify(teamFeignClient, never()).getSubmissionAccess(anyLong(), anyLong());
    }

    @Test
    void resumeSameActiveFileAndReturnUploadedChunks() {
        UploadInitializeRequest request = request(10L);
        SubmissionUpload upload = upload(10L);
        SubmissionUploadChunk chunk = chunk(upload, 0, 10L, request.getFileSha256());
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        when(uploadMapper.selectActiveByTeamId(1L)).thenReturn(upload);
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(chunk));

        UploadSessionVO result = service.initialize(request, 10L);

        assertEquals("upload-token", result.getUploadId());
        assertEquals(List.of(0), result.getUploadedChunks());
        verify(uploadMapper, never()).insert(any(SubmissionUpload.class));
    }

    @Test
    void rejectInitializationWhenUserCannotSubmitForTeam() {
        TeamSubmissionAccessDTO denied = access();
        denied.setMember(false);
        denied.setCanSubmit(false);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(denied));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.initialize(request(10L), 10L)
        );

        assertEquals(SubmissionErrorCode.NOT_TEAM_MEMBER.getCode(), error.getCode());
        verify(uploadMapper, never()).insert(any(SubmissionUpload.class));
    }

    @Test
    void acceptRepeatedChunkWhenContentMatches() {
        byte[] bytes = "1234567890".getBytes();
        String checksum = sha256(bytes);
        SubmissionUpload upload = upload(bytes.length);
        SubmissionUploadChunk existing = chunk(upload, 0, bytes.length, checksum);
        MockMultipartFile file = new MockMultipartFile(
                "file", "chunk", "application/octet-stream", bytes
        );
        when(uploadMapper.selectByToken("upload-token")).thenReturn(upload);
        when(chunkMapper.selectByUploadAndIndex(upload.getId(), 0)).thenReturn(existing);
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(existing));

        UploadSessionVO result = service.uploadChunk("upload-token", 0, checksum, file, 10L);

        assertEquals(List.of(0), result.getUploadedChunks());
        verify(chunkStorage, never()).upload(anyString(), any());
    }

    @Test
    void rejectCompletionWhenAChunkIsMissing() {
        SubmissionUpload upload = upload(6L * 1024 * 1024);
        upload.setTotalChunks(2);
        when(uploadMapper.selectByToken("upload-token")).thenReturn(upload);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.complete("upload-token", 10L)
        );

        assertEquals(SubmissionErrorCode.CHUNK_MISSING.getCode(), error.getCode());
        verify(chunkStorage, never()).compose(anyString(), any());
    }

    @Test
    void completeUploadCreatesOneSubmissionAndTriggersReview() {
        byte[] pdf = "%PDF-test".getBytes();
        SubmissionUpload upload = upload(pdf.length);
        upload.setFileSha256(sha256(pdf));
        SubmissionUploadChunk chunk = chunk(upload, 0, pdf.length, sha256(pdf));
        Submission submission = new Submission();
        submission.setId(101L);
        submission.setTeamId(1L);
        submission.setProblemId(100L);
        when(uploadMapper.selectByToken("upload-token")).thenReturn(upload);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(chunk));
        when(uploadMapper.claimCompletion(anyLong(), any(), any())).thenReturn(1);
        when(storageService.download(upload.getFinalObjectName()))
                .thenReturn(new ByteArrayInputStream(pdf));
        when(persistenceService.createSubmission(upload.getId())).thenReturn(submission);
        when(submissionService.triggerReview(submission))
                .thenReturn(SubmissionVO.builder().id(101L).build());

        SubmissionVO result = service.complete("upload-token", 10L);

        assertEquals(101L, result.getId());
        verify(chunkStorage).compose(upload.getFinalObjectName(), List.of(chunk.getObjectName()));
        verify(uploadMapper).markCompleted(upload.getId(), 101L);
        verify(submissionService).triggerReview(submission);
    }

    @Test
    void rejectInvalidMergedPdfAndReleaseCompletionClaim() {
        byte[] invalidPdf = "not-a-pdf".getBytes();
        SubmissionUpload upload = upload(invalidPdf.length);
        upload.setFileSha256(sha256(invalidPdf));
        SubmissionUploadChunk chunk = chunk(upload, 0, invalidPdf.length, sha256(invalidPdf));
        when(uploadMapper.selectByToken("upload-token")).thenReturn(upload);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(chunk));
        when(uploadMapper.claimCompletion(anyLong(), any(), any())).thenReturn(1);
        when(storageService.download(upload.getFinalObjectName()))
                .thenReturn(new ByteArrayInputStream(invalidPdf));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.complete("upload-token", 10L)
        );

        assertEquals(SubmissionErrorCode.PDF_ONLY.getCode(), error.getCode());
        verify(uploadMapper).resetCompletion(upload.getId());
        verify(storageService).delete(upload.getFinalObjectName());
        verify(persistenceService, never()).createSubmission(anyLong());
    }

    @Test
    void rejectConcurrentCompletionWhileLeaseIsFresh() {
        SubmissionUpload upload = upload(9L);
        SubmissionUpload completing = upload(9L);
        completing.setStatus("COMPLETING");
        completing.setCompletingAt(LocalDateTime.now());
        SubmissionUploadChunk chunk = chunk(upload, 0, 9L, "b".repeat(64));
        when(uploadMapper.selectByToken("upload-token")).thenReturn(upload, completing);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(chunk));
        when(uploadMapper.claimCompletion(anyLong(), any(), any())).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.complete("upload-token", 10L)
        );

        assertEquals(SubmissionErrorCode.UPLOAD_COMPLETING.getCode(), error.getCode());
        verify(chunkStorage, never()).compose(anyString(), any());
        verify(persistenceService, never()).createSubmission(anyLong());
    }

    @Test
    void repeatedCompletionOnlyRetriesReviewForLinkedSubmission() {
        SubmissionUpload upload = upload(9L);
        upload.setStatus("COMPLETING");
        upload.setSubmissionId(101L);
        SubmissionUploadChunk chunk = chunk(upload, 0, 9L, "b".repeat(64));
        Submission submission = new Submission();
        submission.setId(101L);
        when(uploadMapper.selectByToken("upload-token")).thenReturn(upload);
        when(submissionService.getSubmission(101L)).thenReturn(submission);
        when(submissionService.triggerReview(submission))
                .thenReturn(SubmissionVO.builder().id(101L).build());
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(chunk));

        SubmissionVO result = service.complete("upload-token", 10L);

        assertEquals(101L, result.getId());
        verify(persistenceService, never()).createSubmission(anyLong());
        verify(chunkStorage, never()).compose(anyString(), any());
        verify(uploadMapper).markCompleted(upload.getId(), 101L);
        verify(submissionService).triggerReview(submission);
    }

    @Test
    void cleanupExpiresStaleUploadAndDeletesItsChunks() {
        SubmissionUpload upload = upload(9L);
        upload.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        SubmissionUploadChunk chunk = chunk(upload, 0, 9L, "b".repeat(64));
        when(uploadMapper.selectExpired(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(upload));
        when(uploadMapper.markTerminal(upload.getId(), "UPLOADING", "EXPIRED")).thenReturn(1);
        when(chunkMapper.selectByUploadId(upload.getId())).thenReturn(List.of(chunk));

        service.cleanupExpiredUploads();

        verify(uploadMapper).markTerminal(upload.getId(), "UPLOADING", "EXPIRED");
        verify(chunkStorage).delete(List.of(chunk.getObjectName()));
        verify(chunkMapper).deleteByUploadId(upload.getId());
    }

    private UploadInitializeRequest request(long fileSize) {
        UploadInitializeRequest request = new UploadInitializeRequest();
        request.setTeamId(1L);
        request.setOriginalFilename("paper.pdf");
        request.setFileSize(fileSize);
        request.setFileSha256("a".repeat(64));
        return request;
    }

    private TeamSubmissionAccessDTO access() {
        return new TeamSubmissionAccessDTO(
                1L,
                100L,
                true,
                true,
                "IN_PROGRESS",
                LocalDateTime.now().plusHours(1),
                null
        );
    }

    private SubmissionUpload upload(long fileSize) {
        SubmissionUpload upload = new SubmissionUpload();
        upload.setId(50L);
        upload.setUploadToken("upload-token");
        upload.setTeamId(1L);
        upload.setProblemId(100L);
        upload.setUploaderId(10L);
        upload.setOriginalFilename("paper.pdf");
        upload.setFileSize(fileSize);
        upload.setFileSha256("a".repeat(64));
        upload.setChunkSize(5L * 1024 * 1024);
        upload.setTotalChunks(1);
        upload.setStatus("UPLOADING");
        upload.setActiveMarker(1);
        upload.setFinalObjectName("submissions/1/upload-token.pdf");
        upload.setExpiresAt(LocalDateTime.now().plusHours(1));
        return upload;
    }

    private SubmissionUploadChunk chunk(SubmissionUpload upload, int index, long size, String checksum) {
        SubmissionUploadChunk chunk = new SubmissionUploadChunk();
        chunk.setUploadId(upload.getId());
        chunk.setChunkIndex(index);
        chunk.setChunkSize(size);
        chunk.setChunkSha256(checksum);
        chunk.setObjectName("submission-uploads/upload-token/" + index);
        return chunk;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
