package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.dto.ContestRequest;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.audit.ProblemAuditEventProducer;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
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
    @Mock ProblemMapper problemMapper;
    @Mock CacheInvalidator cacheInvalidator;
    @Mock ProblemAuditEventProducer audit;
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
    void createContestSuccess() {
        when(contestMapper.exists(any())).thenReturn(false);
        when(contestMapper.insert(any(Contest.class))).thenReturn(1);

        ContestRequest request = new ContestRequest();
        request.setCode("test_code");
        request.setName("测试赛事");

        Contest created = contestService.create(request);

        assertEquals("TEST_CODE", created.getCode());
        assertEquals("测试赛事", created.getName());
        verify(contestMapper).insert(any(Contest.class));
    }

    @Test
    void createContestDuplicateCode() {
        when(contestMapper.exists(any())).thenReturn(true);

        ContestRequest request = new ContestRequest();
        request.setCode("MCM_ICM");
        request.setName("重复赛事");

        BusinessException error = assertThrows(BusinessException.class,
                () -> contestService.create(request));

        assertEquals(ProblemErrorCode.CONTEST_CODE_DUPLICATE.getCode(), error.getCode());
        verify(contestMapper, never()).insert(any(Contest.class));
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

    @Test
    void deleteContestSuccess() {
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(problemMapper.exists(any())).thenReturn(false);
        when(contestMapper.deleteById(1L)).thenReturn(1);

        contestService.delete(1L);

        verify(contestMapper).deleteById(1L);
    }

    @Test
    void deleteContestInUseThrowsException() {
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(problemMapper.exists(any())).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> contestService.delete(1L));

        assertEquals(ProblemErrorCode.CONTEST_IN_USE.getCode(), error.getCode());
        verify(contestMapper, never()).deleteById(1L);
    }
}
