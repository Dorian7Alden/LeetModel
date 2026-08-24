package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.ProblemLink;
import com.leetmodel.problem.entity.ProblemTag;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ProblemLinkMapper;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.mapper.ProblemTagMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.service.impl.ProblemServiceImpl;
import com.leetmodel.problem.vo.ProblemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题目服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemMapper problemMapper;

    @Mock
    private ProblemTagMapper problemTagMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ProblemLinkMapper problemLinkMapper;

    @Mock
    private ContestMapper contestMapper;

    @InjectMocks
    private ProblemServiceImpl problemService;

    private Problem problem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(problemService, "baseMapper", problemMapper);
        problem = new Problem();
        problem.setId(1L);
        problem.setTitle("2026 年测试题目");
        problem.setContestType("CUMCM");
        problem.setDifficulty(2);
        problem.setAverageScore(BigDecimal.ZERO);
        problem.setStatus(1);
        problem.setCreatorId(10L);
        problem.setCreateTime(LocalDateTime.now());
        problem.setUpdateTime(LocalDateTime.now());
        org.mockito.Mockito.lenient().when(contestMapper.selectById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (id == null || id == 999L) return null;
            Contest contest = new Contest();
            contest.setId(id); contest.setCode(id == 1L ? "MCM_ICM" : "CUMCM");
            contest.setName("测试赛事"); contest.setStatus(1);
            return contest;
        });
    }

    @Test
    @DisplayName("分页筛选题目并批量组装标签")
    void pageProblemsSuccess() {
        when(problemMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    IPage<Problem> page = invocation.getArgument(0);
                    page.setRecords(List.of(problem));
                    page.setTotal(1);
                    return page;
                });
        when(problemTagMapper.selectList(any())).thenReturn(List.of(problemTag(1L, 100L)));
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(100L, "优化")));

        ProblemPageQuery query = new ProblemPageQuery();
        query.setPage(1);
        query.setPageSize(10);
        query.setContestType("CUMCM");
        query.setDifficulty(2);
        query.setStatus(1);
        query.setTagId(100L);
        query.setKeyword("测试");

        IPage<ProblemVO> result = problemService.pageProblems(query);

        assertEquals(1, result.getTotal());
        assertEquals("2026 年测试题目", result.getRecords().get(0).getTitle());
        assertEquals(List.of("优化"), result.getRecords().get(0).getTagNames());
        verify(problemMapper).selectPage(any(IPage.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("查询题目详情成功并组装标签和链接")
    void getProblemDetailSuccess() {
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(problemTagMapper.selectList(any())).thenReturn(List.of(problemTag(1L, 100L)));
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(100L, "预测")));
        when(problemLinkMapper.selectList(any())).thenReturn(List.of(link(200L, 1L, "数据集")));

        ProblemVO result = problemService.getProblemDetail(1L);

        assertEquals(List.of("预测"), result.getTagNames());
        assertEquals(1, result.getLinks().size());
        assertEquals("数据集", result.getLinks().get(0).getTitle());
    }

    @Test
    @DisplayName("查询题目详情失败 —— 题目不存在")
    void getProblemDetailNotFound() {
        when(problemMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.getProblemDetail(999L)
        );

        assertEquals(ProblemErrorCode.PROBLEM_NOT_FOUND.getCode(), exception.getCode());
        verify(problemTagMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("公开详情拒绝访问未发布题目")
    void getPublishedProblemDetailRejectsDraft() {
        problem.setStatus(0);
        when(problemMapper.selectById(1L)).thenReturn(problem);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.getPublishedProblemDetail(1L)
        );

        assertEquals(ProblemErrorCode.PROBLEM_NOT_FOUND.getCode(), exception.getCode());
        verify(problemTagMapper, never()).selectList(any());
        verify(problemLinkMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("创建题目成功并保存标签和链接")
    void createProblemSuccess() {
        when(problemMapper.insert(any(Problem.class))).thenAnswer(invocation -> {
            Problem entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(100L, "规划")));
        when(problemTagMapper.insert(any(ProblemTag.class))).thenReturn(1);
        when(problemLinkMapper.insert(any(ProblemLink.class))).thenAnswer(invocation -> {
            ProblemLink entity = invocation.getArgument(0);
            entity.setId(200L);
            return 1;
        });

        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setTitle("新题目");
        request.setContestType("MCM_ICM");
        request.setDifficulty(3);
        request.setTagIds(List.of(100L));
        request.setLinks(List.of(new ProblemCreateRequest.LinkItem(
                "参考资料", "https://example.com", "说明", 1
        )));

        ProblemVO result = problemService.createProblem(request, 10L);

        assertNotNull(result);
        assertEquals("新题目", result.getTitle());
        assertEquals(0, result.getStatus());
        assertEquals(10L, result.getCreatorId());
        assertEquals(List.of("规划"), result.getTagNames());
        assertEquals(1, result.getLinks().size());
        verify(problemMapper).insert(any(Problem.class));
        verify(problemTagMapper).insert(any(ProblemTag.class));
        verify(problemLinkMapper).insert(any(ProblemLink.class));
    }

    @Test
    @DisplayName("创建题目失败 —— 赛事类型不合法")
    void createProblemInvalidContestType() {
        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setContestId(999L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.createProblem(request, 10L)
        );

        assertEquals(ProblemErrorCode.CONTEST_NOT_FOUND.getCode(), exception.getCode());
        verify(problemMapper, never()).insert(any(Problem.class));
    }

    @Test
    @DisplayName("创建题目失败 —— 标签不存在")
    void createProblemTagNotFound() {
        when(problemMapper.insert(any(Problem.class))).thenAnswer(invocation -> {
            Problem entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(100L, "规划")));

        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setTitle("新题目");
        request.setContestType("CUMCM");
        request.setDifficulty(2);
        request.setTagIds(List.of(100L, 999L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.createProblem(request, 10L)
        );

        assertEquals(ProblemErrorCode.TAG_NOT_FOUND.getCode(), exception.getCode());
        verify(problemTagMapper, never()).insert(any(ProblemTag.class));
    }

    @Test
    @DisplayName("创建题目时自动去除重复标签")
    void createProblemDeduplicatesTags() {
        when(problemMapper.insert(any(Problem.class))).thenAnswer(invocation -> {
            Problem entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(100L, "规划")));
        when(problemTagMapper.insert(any(ProblemTag.class))).thenReturn(1);

        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setTitle("新题目");
        request.setContestType("CUMCM");
        request.setDifficulty(2);
        request.setTagIds(List.of(100L, 100L));

        ProblemVO result = problemService.createProblem(request, 10L);

        assertEquals(List.of("规划"), result.getTagNames());
        verify(problemTagMapper, times(1)).insert(any(ProblemTag.class));
    }

    @Test
    @DisplayName("更新题目成功并替换标签和链接")
    void updateProblemSuccess() {
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(problemMapper.updateById(any(Problem.class))).thenReturn(1);
        when(problemTagMapper.selectList(any())).thenReturn(List.of());
        when(problemLinkMapper.selectList(any())).thenReturn(List.of());
        when(problemTagMapper.delete(any())).thenReturn(1);
        when(tagMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tag(101L, "评价")));
        when(problemTagMapper.insert(any(ProblemTag.class))).thenReturn(1);
        when(problemLinkMapper.delete(any())).thenReturn(1);
        when(problemLinkMapper.insert(any(ProblemLink.class))).thenReturn(1);

        ProblemUpdateRequest request = new ProblemUpdateRequest();
        request.setTitle("更新后的题目");
        request.setContestType("MCM_ICM");
        request.setDifficulty(3);
        request.setStatus(2);
        request.setTagIds(List.of(101L));
        request.setLinks(List.of(new ProblemUpdateRequest.LinkItem(
                "更新资料", "https://example.com/new", null, 0
        )));

        ProblemVO result = problemService.updateProblem(1L, request);

        assertEquals("更新后的题目", result.getTitle());
        assertEquals("MCM_ICM", result.getContestType());
        assertEquals(List.of("评价"), result.getTagNames());
        assertEquals("更新资料", result.getLinks().get(0).getTitle());
        verify(problemMapper).updateById(problem);
        verify(problemTagMapper).delete(any());
        verify(problemLinkMapper).delete(any());
    }

    @Test
    @DisplayName("更新题目失败 —— 题目不存在")
    void updateProblemNotFound() {
        when(problemMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.updateProblem(999L, new ProblemUpdateRequest())
        );

        assertEquals(ProblemErrorCode.PROBLEM_NOT_FOUND.getCode(), exception.getCode());
        verify(problemMapper, never()).updateById(any(Problem.class));
    }

    @Test
    @DisplayName("删除题目成功并清理关联数据")
    void deleteProblemSuccess() {
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(problemTagMapper.delete(any())).thenReturn(1);
        when(problemLinkMapper.delete(any())).thenReturn(1);
        when(problemMapper.deleteById(1L)).thenReturn(1);

        problemService.deleteProblem(1L);

        verify(problemTagMapper).delete(any());
        verify(problemLinkMapper).delete(any());
        verify(problemMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除题目失败 —— 题目不存在")
    void deleteProblemNotFound() {
        when(problemMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.deleteProblem(999L)
        );

        assertEquals(ProblemErrorCode.PROBLEM_NOT_FOUND.getCode(), exception.getCode());
        verify(problemTagMapper, never()).delete(any());
        verify(problemLinkMapper, never()).delete(any());
        verify(problemMapper, never()).deleteById(999L);
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }

    private ProblemTag problemTag(Long problemId, Long tagId) {
        ProblemTag problemTag = new ProblemTag();
        problemTag.setProblemId(problemId);
        problemTag.setTagId(tagId);
        return problemTag;
    }

    private ProblemLink link(Long id, Long problemId, String title) {
        ProblemLink link = new ProblemLink();
        link.setId(id);
        link.setProblemId(problemId);
        link.setTitle(title);
        link.setUrl("https://example.com");
        link.setSortOrder(0);
        return link;
    }
}
