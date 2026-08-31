package com.leetmodel.submission.service;

import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionLock;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.vo.SubmissionVO;
import com.leetmodel.submission.config.ReviewDispatchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {
    @Mock SubmissionMapper submissionMapper; @Mock SubmissionLockMapper lockMapper;
    @Mock TeamFeignClient teamFeignClient; @Mock ReviewFeignClient reviewFeignClient;
    @Mock ProblemFeignClient problemFeignClient;
    @Mock StorageService storageService; @InjectMocks SubmissionService service;
    @Mock ReviewDispatchProperties reviewDispatchProperties;
    @Mock ReviewDispatchQueryService reviewDispatchQueryService;

    @Test
    void markLockedSubmissionAsFinalVersionInHistory() {
        when(teamFeignClient.getTeamInfo(1L)).thenReturn(Result.ok(team()));
        when(teamFeignClient.getMemberIds(1L)).thenReturn(Result.ok(List.of(10L)));
        SubmissionLock lock = new SubmissionLock();
        lock.setSubmissionId(102L);
        when(lockMapper.selectOne(any())).thenReturn(lock);
        Submission first = submission(101L, 1);
        Submission second = submission(102L, 2);
        when(submissionMapper.selectList(any())).thenReturn(List.of(second, first));
        when(storageService.getUrl(anyString())).thenReturn("http://example.test/paper.pdf");

        List<SubmissionVO> history = service.history(1L, 10L);

        assertTrue(history.get(0).getFinalVersion());
        assertFalse(history.get(1).getFinalVersion());
    }

    @Test
    void returnEmptyFinalSnapshotsWhenNoSubmissionIsLocked() {
        when(lockMapper.selectList(null)).thenReturn(List.of());

        List<SubmissionSnapshotDTO> result = service.listFinalSnapshots(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(submissionMapper);
    }

    @Test
    void filterFinalSnapshotsByProblemUsingStableSubmissionIds() {
        SubmissionLock firstLock = new SubmissionLock();
        firstLock.setSubmissionId(101L);
        SubmissionLock secondLock = new SubmissionLock();
        secondLock.setSubmissionId(102L);
        when(lockMapper.selectList(null)).thenReturn(List.of(firstLock, secondLock));
        Submission first = submission(101L, 1);
        first.setProblemId(100L);
        Submission second = submission(102L, 2);
        second.setProblemId(200L);
        when(submissionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(first, second));

        List<SubmissionSnapshotDTO> result = service.listFinalSnapshots(200L);

        assertEquals(1, result.size());
        assertEquals(102L, result.get(0).getId());
        assertTrue(result.get(0).getFinalVersion());
    }

    @Test
    void listRecentSnapshotsMarksOnlyLockedSubmissionAsFinal() {
        SubmissionLock lock = new SubmissionLock();
        lock.setSubmissionId(102L);
        when(lockMapper.selectList(null)).thenReturn(List.of(lock));
        when(submissionMapper.selectList(any())).thenReturn(List.of(
                submission(102L, 2), submission(101L, 1)));

        List<SubmissionSnapshotDTO> result = service.listRecentSnapshots(20);

        assertEquals(2, result.size());
        assertTrue(result.get(0).getFinalVersion());
        assertFalse(result.get(1).getFinalVersion());
        verify(submissionMapper).selectList(any());
    }

    @Test
    void createPreviewUrlOnlyWhenRequestedBySubmissionId() {
        when(submissionMapper.selectById(101L)).thenReturn(submission(101L, 1));
        when(storageService.getUrl("submissions/1/paper.pdf"))
                .thenReturn("http://minio.test/presigned-paper.pdf");

        SubmissionPreviewDTO preview = service.getPreview(101L);

        assertEquals(101L, preview.getSubmissionId());
        assertEquals("paper.pdf", preview.getOriginalFilename());
        assertEquals("http://minio.test/presigned-paper.pdf", preview.getPreviewUrl());
    }

    @Test
    void mqPrimaryReturnsWaitingDispatchWithoutRequestThreadFeignCall() {
        Submission submission = submission(101L, 1);
        when(reviewDispatchProperties.getTransport())
                .thenReturn(ReviewDispatchProperties.Transport.MQ_PRIMARY);
        when(reviewDispatchQueryService.status(101L)).thenReturn("WAITING_DISPATCH");

        SubmissionVO response = service.triggerReview(submission);

        assertEquals("WAITING_DISPATCH", response.getReviewDispatchStatus());
        verify(reviewFeignClient, never()).createVersionedTask(any(), any(), any(), any());
    }

    @Test
    void legacyModeUsesOnlyIdempotentRequestThreadFeignCall() {
        Submission submission = submission(101L, 1);
        when(reviewDispatchProperties.getTransport())
                .thenReturn(ReviewDispatchProperties.Transport.LEGACY_FEIGN);
        when(reviewFeignClient.createVersionedTask(101L, 1L, 100L, "EVIDENCE_REVIEW_V2"))
                .thenReturn(Result.ok(901L));

        SubmissionVO response = service.triggerReview(submission);

        assertEquals("DISPATCHED", response.getReviewDispatchStatus());
        verify(reviewFeignClient).createVersionedTask(101L, 1L, 100L, "EVIDENCE_REVIEW_V2");
        verify(reviewDispatchQueryService).markLegacyDispatched(101L, 901L);
    }

    private TeamDTO team() {
        return new TeamDTO(1L, "team", 10L, 1, 1, 100L, "IN_PROGRESS",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1), null);
    }
    private Submission submission(long id, int version) {
        Submission value = new Submission();
        value.setId(id); value.setTeamId(1L); value.setProblemId(100L); value.setSubmitterId(10L);
        value.setVersion(version); value.setOriginalFilename("paper.pdf"); value.setFileSize(100L);
        value.setStatus("SUCCESS"); value.setObjectName("submissions/1/paper.pdf");
        return value;
    }
}
