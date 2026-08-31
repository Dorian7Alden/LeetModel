package com.leetmodel.problem.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.service.impl.ContestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContestServiceTest {
    @Mock ContestMapper contestMapper;
    @Mock CacheInvalidator cacheInvalidator;
    @InjectMocks ContestServiceImpl contestService;
    private Contest contest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contestService, "baseMapper", contestMapper);
        contest = new Contest();
        contest.setId(1L);
        contest.setCode("MCM_ICM");
        contest.setName("美国大学生数学建模竞赛");
    }

    @Test
    void updateContestNormalizesCodeAndName() {
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(contestMapper.exists(any())).thenReturn(false);
        when(contestMapper.updateById(any(Contest.class))).thenReturn(1);

        Contest updated = contestService.update(1L, " mcm-icm ", " 美赛 ");

        assertEquals("MCM-ICM", updated.getCode());
        assertEquals("美赛", updated.getName());
        verify(contestMapper).updateById(contest);
    }

    @Test
    void rejectMissingContest() {
        when(contestMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> contestService.update(99L, "MCM", "美赛"));

        assertEquals(ProblemErrorCode.CONTEST_NOT_FOUND.getCode(), error.getCode());
        verify(contestMapper, never()).updateById(any(Contest.class));
    }

    @Test
    void rejectDuplicateContestCode() {
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(contestMapper.exists(any())).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> contestService.update(1L, "CUMCM", "国赛"));

        assertEquals(ProblemErrorCode.CONTEST_CODE_DUPLICATE.getCode(), error.getCode());
        verify(contestMapper, never()).updateById(any(Contest.class));
    }
}
