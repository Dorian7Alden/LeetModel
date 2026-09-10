package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.ProblemFavorite;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemFavoriteMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.service.impl.ProblemFavoriteServiceImpl;
import com.leetmodel.problem.vo.ProblemFavoriteRecordVO;
import com.leetmodel.problem.vo.ProblemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemFavoriteServiceTest {

    @Mock
    private ProblemFavoriteMapper problemFavoriteMapper;

    @Mock
    private ProblemMapper problemMapper;

    @Mock
    private ContestMapper contestMapper;

    @Mock
    private ProblemService problemService;

    @InjectMocks
    private ProblemFavoriteServiceImpl problemFavoriteService;

    @Test
    @DisplayName("添加收藏 - 题目不存在或非公开状态时抛出异常")
    void addFavorite_problemNotFound_throwsException() {
        when(problemMapper.selectById(100L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                problemFavoriteService.addFavorite(1L, 100L)
        );
        assertEquals(ProblemErrorCode.PROBLEM_NOT_FOUND.getCode(), ex.getCode());

        Problem draftProblem = new Problem();
        draftProblem.setId(101L);
        draftProblem.setStatus(0); // 草稿
        when(problemMapper.selectById(101L)).thenReturn(draftProblem);

        assertThrows(BusinessException.class, () ->
                problemFavoriteService.addFavorite(1L, 101L)
        );
    }

    @Test
    @DisplayName("添加收藏 - 首次收藏成功插入，已收藏时幂等返回 true")
    void addFavorite_successAndIdempotent() {
        Problem problem = new Problem();
        problem.setId(100L);
        problem.setStatus(1); // 已发布
        when(problemMapper.selectById(100L)).thenReturn(problem);

        // 首次收藏
        when(problemFavoriteMapper.selectOne(any())).thenReturn(null);
        when(problemFavoriteMapper.insert(any(ProblemFavorite.class))).thenReturn(1);

        boolean result = problemFavoriteService.addFavorite(1L, 100L);
        assertTrue(result);
        verify(problemFavoriteMapper, times(1)).insert(any(ProblemFavorite.class));

        // 重复收藏
        ProblemFavorite existing = ProblemFavorite.builder()
                .id(1L)
                .userId(1L)
                .problemId(100L)
                .createTime(LocalDateTime.now())
                .build();
        when(problemFavoriteMapper.selectOne(any())).thenReturn(existing);

        boolean idempotentResult = problemFavoriteService.addFavorite(1L, 100L);
        assertTrue(idempotentResult);
        verify(problemFavoriteMapper, times(1)).insert(any(ProblemFavorite.class));
    }

    @Test
    @DisplayName("取消收藏 - 成功删除关联记录")
    void removeFavorite_success() {
        when(problemFavoriteMapper.delete(any())).thenReturn(1);

        boolean result = problemFavoriteService.removeFavorite(1L, 100L);
        assertTrue(result);
        verify(problemFavoriteMapper, times(1)).delete(any());
    }

    @Test
    @DisplayName("获取收藏列表 - 按收藏时间升序排列，最先收藏的排在最前")
    void listFavoriteRecords_sortedByCreateTimeAsc() {
        LocalDateTime now = LocalDateTime.now();
        ProblemFavorite fav1 = ProblemFavorite.builder().problemId(101L).createTime(now.minusHours(2)).build();
        ProblemFavorite fav2 = ProblemFavorite.builder().problemId(102L).createTime(now.minusHours(1)).build();

        when(problemFavoriteMapper.selectList(any())).thenReturn(List.of(fav1, fav2));

        List<ProblemFavoriteRecordVO> records = problemFavoriteService.listFavoriteRecords(1L);
        assertEquals(2, records.size());
        assertEquals(101L, records.get(0).getProblemId());
        assertEquals(102L, records.get(1).getProblemId());
        assertTrue(records.get(0).getFavoritedAt() < records.get(1).getFavoritedAt());
    }

    @Test
    @DisplayName("分页获取收藏题目 - 严格保持收藏记录正序")
    void pageFavoriteProblems_keepsFavoriteOrder() {
        LocalDateTime now = LocalDateTime.now();
        ProblemFavorite fav1 = ProblemFavorite.builder().problemId(102L).createTime(now.minusHours(2)).build();
        ProblemFavorite fav2 = ProblemFavorite.builder().problemId(101L).createTime(now.minusHours(1)).build();

        Page<ProblemFavorite> page = new Page<>(1, 10);
        page.setRecords(List.of(fav1, fav2));
        page.setTotal(2);
        when(problemFavoriteMapper.selectPage(any(), any())).thenReturn(page);

        Problem p1 = new Problem();
        p1.setId(101L);
        p1.setCode(1);
        p1.setTitle("题目1");
        p1.setContestId(10L);

        Problem p2 = new Problem();
        p2.setId(102L);
        p2.setCode(2);
        p2.setTitle("题目2");
        p2.setContestId(10L);

        when(problemMapper.selectBatchIds(List.of(102L, 101L))).thenReturn(List.of(p1, p2));

        Contest contest = new Contest();
        contest.setId(10L);
        contest.setName("全国大学生数学建模竞赛");
        when(contestMapper.selectBatchIds(any())).thenReturn(List.of(contest));
        when(problemService.getTagNames(any())).thenReturn(List.of("优化模型"));

        PageResult<ProblemVO> result = problemFavoriteService.pageFavoriteProblems(1L, 1, 10);
        assertEquals(2, result.getTotal());
        assertEquals(2, result.getRows().size());
        // 最先收藏的是 102L，其次是 101L，必须严格保持这个顺序！
        assertEquals(102L, result.getRows().get(0).getId());
        assertEquals(101L, result.getRows().get(1).getId());
    }
}
