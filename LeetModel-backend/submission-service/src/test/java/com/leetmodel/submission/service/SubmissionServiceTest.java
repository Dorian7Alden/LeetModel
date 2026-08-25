package com.leetmodel.submission.service;

import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionLock;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.vo.SubmissionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {
    @Mock SubmissionMapper submissionMapper; @Mock SubmissionLockMapper lockMapper;
    @Mock TeamFeignClient teamFeignClient; @Mock ReviewFeignClient reviewFeignClient;
    @Mock StorageService storageService; @InjectMocks SubmissionService service;

    @Test
    void rejectNonMember() {
        TeamSubmissionAccessDTO access = access();
        access.setMember(false);
        access.setCanSubmit(false);
        when(teamFeignClient.getSubmissionAccess(1L, 20L)).thenReturn(Result.ok(access));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(1L, pdf(), 20L));
        assertEquals(SubmissionErrorCode.NOT_TEAM_MEMBER.getCode(), error.getCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectFakePdf() {
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", "hello".getBytes());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(1L, file, 10L));
        assertEquals(SubmissionErrorCode.PDF_ONLY.getCode(), error.getCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectPdfLargerThanTwentyMegabytes() {
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access()));
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf",
                new byte[20 * 1024 * 1024 + 1]);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(1L, file, 10L));

        assertEquals(SubmissionErrorCode.PDF_SIZE_EXCEEDED.getCode(), error.getCode());
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectMemberWithoutSubmissionPermission() {
        TeamSubmissionAccessDTO access = access();
        access.setCanSubmit(false);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(1L, pdf(), 10L));

        assertEquals(SubmissionErrorCode.SUBMISSION_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(storageService);
    }

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

    private TeamDTO team() {
        return new TeamDTO(1L, "team", 10L, 1, 1, 100L, "IN_PROGRESS",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1), null);
    }
    private TeamSubmissionAccessDTO access() {
        return new TeamSubmissionAccessDTO(1L, 100L, true, true, "IN_PROGRESS",
                LocalDateTime.now().plusHours(1), null);
    }
    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "paper.pdf", "application/pdf", "%PDF-test".getBytes());
    }
    private Submission submission(long id, int version) {
        Submission value = new Submission();
        value.setId(id); value.setTeamId(1L); value.setProblemId(100L); value.setSubmitterId(10L);
        value.setVersion(version); value.setOriginalFilename("paper.pdf"); value.setFileSize(100L);
        value.setStatus("SUCCESS"); value.setObjectName("submissions/1/paper.pdf");
        return value;
    }
}
