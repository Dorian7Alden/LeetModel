package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.ProblemAttachment;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemAttachmentMapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题目服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock private ProblemMapper problemMapper;
    @Mock private ProblemTagMapper problemTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private ProblemAttachmentMapper problemAttachmentMapper;
    @Mock private ContestMapper contestMapper;
    @Mock private ObjectProvider<StorageService> storageServiceProvider;
    @Mock private StorageService storageService;
    @Mock private CacheInvalidator cacheInvalidator;

    @InjectMocks
    private ProblemServiceImpl problemService;

    private Problem problem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(problemService, "baseMapper", problemMapper);
        problem = new Problem();
        problem.setId(1L);
        problem.setTitle("测试题目");
        problem.setContentMarkdown("## 题面");
        problem.setContestId(10L);
        problem.setYear(2026);
        problem.setStatementLanguage("ZH");
        problem.setDurationMinutes(4320);
        problem.setDifficulty(2);
        problem.setAverageScore(BigDecimal.ZERO);
        problem.setStatus(1);
        problem.setCreatorId(100L);
        problem.setCreateTime(LocalDateTime.now());
        problem.setUpdateTime(LocalDateTime.now());

        Contest contest = new Contest();
        contest.setId(10L);
        contest.setCode("CUSTOM_CONTEST");
        contest.setName("自定义赛事");
        org.mockito.Mockito.lenient().when(contestMapper.selectById(10L)).thenReturn(contest);
    }

    @Test
    @DisplayName("分页查询不返回题面和附件")
    void pageProblemsReturnsSummary() {
        when(problemMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> {
                    IPage<Problem> page = invocation.getArgument(0);
                    page.setRecords(List.of(problem));
                    page.setTotal(1);
                    return page;
                });
        when(problemTagMapper.selectList(any())).thenReturn(List.of());

        IPage<ProblemVO> result = problemService.pageProblems(new ProblemPageQuery());

        assertEquals(1, result.getTotal());
        assertNull(result.getRecords().get(0).getContentMarkdown());
        assertNull(result.getRecords().get(0).getAttachments());
    }

    @Test
    @DisplayName("不同类型标签使用 AND 条件组合筛选")
    void pageProblemsCombinesDifferentTagTypesWithAnd() {
        Tag domain = tag(6001L, "环境生态", "BACKGROUND_DOMAIN");
        Tag problemType = tag(6101L, "预测", "PROBLEM_TYPE");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(domain, problemType));
        when(problemMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ProblemPageQuery query = new ProblemPageQuery();
        query.setTagIds(List.of(6001L, 6101L));

        problemService.pageProblems(query);

        ArgumentCaptor<Wrapper<Problem>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(problemMapper).selectPage(any(IPage.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("tag_id = 6001"));
        assertTrue(sql.contains("tag_id = 6101"));
        assertTrue(sql.contains("AND"));
    }

    @Test
    @DisplayName("分页查询按平均分降序排列")
    void pageProblemsSortsByAverageScoreDescending() {
        when(problemMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ProblemPageQuery query = new ProblemPageQuery();
        query.setSortBy("averageScore");
        query.setSortOrder("desc");

        problemService.pageProblems(query);

        ArgumentCaptor<Wrapper<Problem>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(problemMapper).selectPage(any(IPage.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("average_score DESC"));
    }

    @Test
    @DisplayName("分页查询拒绝反向历史分数区间")
    void pageProblemsRejectsReversedScoreRange() {
        ProblemPageQuery query = new ProblemPageQuery();
        query.setMinAverageScore(new BigDecimal("90"));
        query.setMaxAverageScore(new BigDecimal("80"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.pageProblems(query)
        );

        assertEquals(ProblemErrorCode.INVALID_SCORE_RANGE.getCode(), exception.getCode());
        verify(problemMapper, never()).selectPage(any(IPage.class), any(Wrapper.class));
    }

    @Test
    @DisplayName("题目详情返回 Markdown 和多个附件")
    void getProblemDetailReturnsMarkdownAndAttachments() {
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(problemTagMapper.selectList(any())).thenReturn(List.of());
        when(problemAttachmentMapper.selectList(any())).thenReturn(List.of(
                attachment(201L, "data.xlsx", "problems/1/data.xlsx"),
                attachment(202L, "statement.pdf", "problems/1/statement.pdf")
        ));
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);
        when(storageService.getUrl(any())).thenReturn("https://example.com/download");

        ProblemVO result = problemService.getProblemDetail(1L);

        assertEquals("## 题面", result.getContentMarkdown());
        assertEquals(2, result.getAttachments().size());
        assertEquals("data.xlsx", result.getAttachments().get(0).getFileName());
    }

    @Test
    @DisplayName("公开详情拒绝未发布题目")
    void getPublishedProblemDetailRejectsDraft() {
        problem.setStatus(0);
        when(problemMapper.selectById(1L)).thenReturn(problem);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.getPublishedProblemDetail(1L)
        );

        assertEquals(ProblemErrorCode.PROBLEM_NOT_FOUND.getCode(), exception.getCode());
        verify(problemAttachmentMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("创建题目保存 Markdown 和自定义赛事")
    void createProblemStoresMarkdownAndCustomContest() {
        when(problemMapper.insert(any(Problem.class))).thenAnswer(invocation -> {
            Problem entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
        ProblemCreateRequest request = validCreateRequest();
        request.setContentMarkdown("# 新题面");

        ProblemVO result = problemService.createProblem(request, 100L);

        assertEquals("# 新题面", result.getContentMarkdown());
        assertEquals("CUSTOM_CONTEST", result.getContestCode());
        verify(problemMapper).insert(any(Problem.class));
    }

    @Test
    @DisplayName("创建题目拒绝不存在的自定义赛事")
    void createProblemRejectsMissingContest() {
        when(contestMapper.selectById(999L)).thenReturn(null);
        ProblemCreateRequest request = validCreateRequest();
        request.setContestId(999L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.createProblem(request, 100L)
        );

        assertEquals(ProblemErrorCode.CONTEST_NOT_FOUND.getCode(), exception.getCode());
        verify(problemMapper, never()).insert(any(Problem.class));
    }

    @Test
    @DisplayName("创建题目拒绝同类型的多个标签")
    void createProblemRejectsTagsOfSameType() {
        Tag first = tag(6001L, "环境生态", "BACKGROUND_DOMAIN");
        Tag second = tag(6002L, "交通物流", "BACKGROUND_DOMAIN");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(first, second));
        ProblemCreateRequest request = validCreateRequest();
        request.setTagIds(List.of(6001L, 6002L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> problemService.createProblem(request, 100L)
        );

        assertEquals(ProblemErrorCode.TAG_TYPE_CONFLICT.getCode(), exception.getCode());
    }

    @Test
    @DisplayName("创建题目允许关联多个模型算法标签")
    void createProblemAcceptsMultipleModelAlgorithmTags() {
        Tag first = tag(6201L, "回归分析", "MODEL_ALGORITHM");
        Tag second = tag(6203L, "线性规划", "MODEL_ALGORITHM");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(first, second));
        ProblemCreateRequest request = validCreateRequest();
        request.setTagIds(List.of(6201L, 6203L));

        ProblemVO result = problemService.createProblem(request, 100L);

        assertEquals(List.of("回归分析", "线性规划"), result.getTagNames());
    }

    @Test
    @DisplayName("更新题目可清空 Markdown")
    void updateProblemClearsMarkdown() {
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(problemMapper.updateById(any(Problem.class))).thenReturn(1);
        when(problemTagMapper.selectList(any())).thenReturn(List.of());
        when(problemAttachmentMapper.selectList(any())).thenReturn(List.of());

        ProblemUpdateRequest request = new ProblemUpdateRequest();
        request.setContentMarkdown("");
        ProblemVO result = problemService.updateProblem(1L, request);

        assertNull(result.getContentMarkdown());
        verify(problemMapper).updateById(problem);
    }

    @Test
    @DisplayName("上传附件保存对象路径和元数据")
    void uploadAttachmentStoresObjectAndMetadata() {
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);
        when(storageService.upload(any(), any())).thenReturn("problems/1/attachments/file.pdf");
        when(storageService.getUrl(any())).thenReturn("https://example.com/file.pdf");
        when(problemAttachmentMapper.insert(any(ProblemAttachment.class))).thenAnswer(invocation -> {
            ProblemAttachment attachment = invocation.getArgument(0);
            attachment.setId(201L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf", "pdf".getBytes()
        );

        ProblemVO.AttachmentVO result = problemService.uploadAttachment(
                1L, file, "原始题面", 1
        );

        assertEquals("statement.pdf", result.getFileName());
        assertEquals("原始题面", result.getDescription());
        verify(storageService).upload(file, "problems/1/attachments");
        verify(problemAttachmentMapper).insert(any(ProblemAttachment.class));
    }

    @Test
    @DisplayName("删除附件清理元数据和对象")
    void deleteAttachmentRemovesMetadataAndObject() {
        ProblemAttachment attachment = attachment(
                201L,
                "statement.pdf",
                "problems/1/attachments/file.pdf"
        );
        when(problemAttachmentMapper.selectById(201L)).thenReturn(attachment);
        when(problemAttachmentMapper.deleteById(201L)).thenReturn(1);
        when(storageServiceProvider.getIfAvailable()).thenReturn(storageService);

        problemService.deleteAttachment(1L, 201L);

        verify(problemAttachmentMapper).deleteById(201L);
        verify(storageService).delete("problems/1/attachments/file.pdf");
    }

    private ProblemCreateRequest validCreateRequest() {
        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setTitle("新题目");
        request.setContestId(10L);
        request.setYear(2026);
        request.setStatementLanguage("ZH");
        request.setDurationMinutes(4320);
        request.setDifficulty(2);
        return request;
    }

    private ProblemAttachment attachment(Long id, String fileName, String objectKey) {
        ProblemAttachment attachment = new ProblemAttachment();
        attachment.setId(id);
        attachment.setProblemId(1L);
        attachment.setFileName(fileName);
        attachment.setObjectKey(objectKey);
        attachment.setContentType("application/octet-stream");
        attachment.setFileSize(10L);
        attachment.setSortOrder(0);
        return attachment;
    }

    private Tag tag(Long id, String name, String type) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setType(type);
        return tag;
    }
}
