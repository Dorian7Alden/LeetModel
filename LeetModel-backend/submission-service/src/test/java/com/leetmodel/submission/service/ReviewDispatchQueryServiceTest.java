package com.leetmodel.submission.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewDispatchQueryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void cancelledDraftDispatchIsReportedAsNotRequested() {
        when(jdbcTemplate.queryForList(
                anyString(), eq(String.class), eq("REVIEW_TASK_READY"),
                eq("review:101:DEEP_EVIDENCE_REVIEW_V4")))
                .thenReturn(List.of("CANCELLED"));

        ReviewDispatchQueryService service = new ReviewDispatchQueryService(jdbcTemplate);

        assertEquals("NOT_REQUESTED", service.status(101L));
    }
}
