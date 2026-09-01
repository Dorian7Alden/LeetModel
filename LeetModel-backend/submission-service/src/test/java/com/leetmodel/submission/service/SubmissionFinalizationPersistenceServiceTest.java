package com.leetmodel.submission.service;

import com.leetmodel.common.api.dto.FinalSubmissionChangedPayload;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionLock;
import com.leetmodel.submission.mapper.SubmissionLockMapper;
import com.leetmodel.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionFinalizationPersistenceServiceTest {
    @Mock SubmissionLockMapper lockMapper;
    @Mock SubmissionMapper submissionMapper;
    @Mock MessageOutbox messageOutbox;
    private SubmissionFinalizationPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new SubmissionFinalizationPersistenceService(
                lockMapper,
                submissionMapper,
                new MessageEnvelopeFactory("submission-service",
                        Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC)),
                messageOutbox);
    }

    @Test
    void lockAndFinalEventAreCreatedTogether() {
        Submission submission = submission();
        when(submissionMapper.selectOne(any())).thenReturn(submission);

        Submission result = service.lockFinal(team());

        assertThat(result).isSameAs(submission);
        verify(lockMapper).insert(any(SubmissionLock.class));
        ArgumentCaptor<MessageEnvelopeV1<?>> envelope = ArgumentCaptor.forClass(MessageEnvelopeV1.class);
        verify(messageOutbox).enqueue(eq("submission-event-v1"),
                eq("FINAL_SUBMISSION_CHANGED"), envelope.capture());
        FinalSubmissionChangedPayload payload =
                (FinalSubmissionChangedPayload) envelope.getValue().payload();
        assertThat(payload.submissionId()).isEqualTo(101L);
        assertThat(payload.problemId()).isEqualTo(51L);
    }

    @Test
    void existingLockRepairsMissingEventAndDuplicateEventIsIdempotent() {
        SubmissionLock lock = new SubmissionLock();
        lock.setTeamId(41L);
        lock.setSubmissionId(101L);
        lock.setLockedAt(LocalDateTime.of(2026, 9, 1, 8, 0));
        when(lockMapper.selectOne(any())).thenReturn(lock);
        when(submissionMapper.selectById(101L)).thenReturn(submission());
        when(messageOutbox.enqueue(any(), any(), any()))
                .thenThrow(new DuplicateKeyException("already enqueued"));

        assertThat(service.lockFinal(team()).getId()).isEqualTo(101L);
    }

    private TeamDTO team() {
        return new TeamDTO(41L, "team", 1L, 1, 1, 51L, "ENDED",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1),
                LocalDateTime.now().minusHours(1));
    }

    private Submission submission() {
        Submission submission = new Submission();
        submission.setId(101L);
        submission.setTeamId(41L);
        submission.setProblemId(51L);
        submission.setStatus("SUCCESS");
        return submission;
    }
}
