package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.vo.ProblemVO;
import com.leetmodel.common.api.dto.AssistantProblemQueryDTO;
import com.leetmodel.common.api.dto.AssistantProblemQueryMode;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 基于 Flyway 演示数据验证公开题库动态查询语义。
 */
@SpringBootTest
class ProblemQueryIntegrationTest {

    @Autowired
    private ProblemService problemService;

    @Test
    @DisplayName("赛事、年份、语言、难度、分数、关键词和标签可以动态组合")
    void combinesAllSupportedFilters() {
        ProblemPageQuery query = publishedQuery();
        query.setContestId(3L);
        query.setYear(2026);
        query.setStatementLanguage("ZH");
        query.setDifficulty(3);
        query.setMinAverageScore(new BigDecimal("90"));
        query.setMaxAverageScore(new BigDecimal("95"));
        query.setKeyword("物资");
        query.setTagIds(List.of(6002L, 6103L, 6203L));

        IPage<ProblemVO> result = problemService.pageProblems(query);

        assertEquals(1, result.getTotal());
        assertEquals("应急物资配送优化", result.getRecords().get(0).getTitle());
        assertTrue(result.getRecords().get(0).getTagNames()
                .containsAll(List.of("交通物流", "优化", "线性规划")));
    }

    @Test
    @DisplayName("不同类型标签按 AND 匹配")
    void filtersTagsWithAndSemantics() {
        ProblemPageQuery query = publishedQuery();
        query.setTagIds(List.of(6002L, 6103L));

        IPage<ProblemVO> result = problemService.pageProblems(query);

        assertEquals(3, result.getTotal());
        assertTrue(result.getRecords().stream()
                .allMatch(problem -> problem.getTagNames().containsAll(List.of("交通物流", "优化"))));
    }

    @Test
    @DisplayName("公开查询条件不会返回匹配的草稿题目")
    void excludesDraftProblems() {
        ProblemPageQuery query = publishedQuery();
        query.setContestId(3L);
        query.setYear(2026);
        query.setKeyword("未发布");

        IPage<ProblemVO> result = problemService.pageProblems(query);

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("年份、难度和平均分支持升序与降序排列")
    void sortsBySupportedFieldsInBothDirections() {
        assertSorted("year", true);
        assertSorted("year", false);
        assertSorted("difficulty", true);
        assertSorted("difficulty", false);
        assertSorted("averageScore", true);
        assertSorted("averageScore", false);
    }

    @Test
    @DisplayName("客服按题号只返回已发布题目及受限题面概览")
    void assistantSearchesPublishedProblemByCode() {
        AssistantProblemQueryDTO query = new AssistantProblemQueryDTO();
        query.setMode(AssistantProblemQueryMode.SEARCH);
        query.setCode(1001);
        query.setIncludeOverview(true);
        query.setLimit(5);

        var result = problemService.queryForAssistant(query);

        assertEquals("CODE", result.getMatchType());
        assertEquals(1, result.getItems().size());
        assertEquals(1001, result.getItems().get(0).getCode());
        assertEquals("CUMCM", result.getItems().get(0).getContestCode());
        assertTrue(result.getItems().get(0).getOverview().codePointCount(
                0, result.getItems().get(0).getOverview().length()) <= 500);
    }

    @Test
    @DisplayName("客服推荐关键词同时匹配标题和标签并稳定排序")
    void assistantRecommendationMatchesTagsAndUsesStableOrder() {
        AssistantProblemQueryDTO query = new AssistantProblemQueryDTO();
        query.setMode(AssistantProblemQueryMode.RECOMMEND);
        query.setKeyword("线性规划");
        query.setLimit(5);

        var result = problemService.queryForAssistant(query);

        assertEquals(List.of(1007, 1001), result.getItems().stream()
                .map(item -> item.getCode()).toList());
        assertEquals(List.of("keyword:线性规划"), result.getMatchedConditions());
        assertTrue(result.getItems().stream()
                .allMatch(item -> item.getTagNames().contains("线性规划")));
    }

    @Test
    @DisplayName("客服无条件推荐默认三条且标记候选截断")
    void assistantRecommendationUsesDefaultLimitAndExcludesDrafts() {
        AssistantProblemQueryDTO query = new AssistantProblemQueryDTO();
        query.setMode(AssistantProblemQueryMode.RECOMMEND);

        var result = problemService.queryForAssistant(query);

        assertEquals(List.of(1006, 1007, 1008), result.getItems().stream()
                .map(item -> item.getCode()).toList());
        assertEquals(3, result.getItems().size());
        assertTrue(result.getTruncated());
        assertTrue(result.getItems().stream().noneMatch(item -> item.getCode() == 1009));
    }

    @Test
    @DisplayName("客服查询不存在的赛事编码返回明确业务错误")
    void assistantRecommendationRejectsUnknownContest() {
        AssistantProblemQueryDTO query = new AssistantProblemQueryDTO();
        query.setMode(AssistantProblemQueryMode.RECOMMEND);
        query.setContestCode("UNKNOWN");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> problemService.queryForAssistant(query));

        assertEquals(40408, exception.getCode());
    }

    private void assertSorted(String sortBy, boolean ascending) {
        ProblemPageQuery query = publishedQuery();
        query.setPageSize(100);
        query.setSortBy(sortBy);
        query.setSortOrder(ascending ? "asc" : "desc");

        List<ProblemVO> records = problemService.pageProblems(query).getRecords();

        assertTrue(records.size() > 1);
        for (int index = 1; index < records.size(); index++) {
            int comparison = comparableValue(records.get(index - 1), sortBy)
                    .compareTo(comparableValue(records.get(index), sortBy));
            assertTrue(ascending ? comparison <= 0 : comparison >= 0,
                    () -> sortBy + " 未按 " + query.getSortOrder() + " 排列");
        }
    }

    private BigDecimal comparableValue(ProblemVO problem, String sortBy) {
        return switch (sortBy) {
            case "year" -> BigDecimal.valueOf(problem.getYear());
            case "difficulty" -> BigDecimal.valueOf(problem.getDifficulty());
            case "averageScore" -> problem.getAverageScore();
            default -> throw new IllegalArgumentException("不支持的排序字段：" + sortBy);
        };
    }

    private ProblemPageQuery publishedQuery() {
        ProblemPageQuery query = new ProblemPageQuery();
        query.setStatus(1);
        return query;
    }
}
