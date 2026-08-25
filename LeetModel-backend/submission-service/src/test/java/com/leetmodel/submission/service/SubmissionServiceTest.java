package com.leetmodel.submission.service;

import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.submission.enums.SubmissionErrorCode;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
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
    void rejectMemberWithoutSubmissionPermission() {
        TeamSubmissionAccessDTO access = access();
        access.setCanSubmit(false);
        when(teamFeignClient.getSubmissionAccess(1L, 10L)).thenReturn(Result.ok(access));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(1L, pdf(), 10L));

        assertEquals(SubmissionErrorCode.SUBMISSION_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(storageService);
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
}
