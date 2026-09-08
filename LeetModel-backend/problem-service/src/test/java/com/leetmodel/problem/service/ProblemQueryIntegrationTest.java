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
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("现有测试题目均且仅分配一个 A-F 或 X 题号")
    void assignsOneSupportedNumberToEveryExistingProblem() {
        Integer unassignedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM problem
                WHERE problem_number NOT IN ('A', 'B', 'C', 'D', 'E', 'F', 'X')
                """, Integer.class);

        assertEquals(0, unassignedCount);
    }

    @Test
    @DisplayName("题号字段收紧为单字符且默认使用 X")
    void usesXAsProblemNumberSchemaDefault() {
        String columnDefinition = jdbcTemplate.queryForObject("""
                SELECT CONCAT(DATA_TYPE, ':', CHARACTER_MAXIMUM_LENGTH, ':', COLUMN_DEFAULT)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'problem'
                  AND COLUMN_NAME = 'problem_number'
                """, String.class);

        assertEquals("char:1:X", columnDefinition);
    }

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
    @DisplayName("公开题库查询返回的题目包含多个题目类型标签样例")
    void returnsProblemsWithMultipleProblemTypeTags() {
        ProblemPageQuery query = publishedQuery();
        query.setKeyword("城市共享单车潮汐调度");

        IPage<ProblemVO> result = problemService.pageProblems(query);

        assertEquals(1, result.getTotal());
        ProblemVO bikeProblem = result.getRecords().get(0);
        assertTrue(bikeProblem.getTagNames().containsAll(List.of("优化", "预测")),
                "单车题目未包含预期的多题目类型标签，实际标签：" + bikeProblem.getTagNames());

        query.setKeyword("湖泊水质变化预测");
        IPage<ProblemVO> lakeResult = problemService.pageProblems(query);
        assertEquals(1, lakeResult.getTotal());
        ProblemVO lakeProblem = lakeResult.getRecords().get(0);
        assertTrue(lakeProblem.getTagNames().containsAll(List.of("预测", "评价")),
                "水质题目未包含预期的多题目类型标签，实际标签：" + lakeProblem.getTagNames());
    }

    @Test
    @DisplayName("公开题库可以按赛事题号筛选完整赛题")
    void filtersByProblemNumber() {
        ProblemPageQuery query = publishedQuery();
        query.setProblemNumber("A");
        query.setPageSize(100);

        IPage<ProblemVO> result = problemService.pageProblems(query);

        assertTrue(result.getTotal() >= 3);
        assertTrue(result.getRecords().stream()
                .allMatch(problem -> "A".equals(problem.getProblemNumber())));
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
    @DisplayName("题号、年份、难度和平均分支持升序与降序排列")
    void sortsBySupportedFieldsInBothDirections() {
        assertSorted("code", true);
        assertSorted("code", false);
        assertSorted("year", true);
        assertSorted("year", false);
        assertSorted("difficulty", true);
        assertSorted("difficulty", false);
        assertSorted("averageScore", true);
        assertSorted("averageScore", false);
    }

    @Test
    @DisplayName("默认分页查询按题号从小到大排序")
    void defaultPageQuerySortsByCodeAscending() {
        ProblemPageQuery query = publishedQuery();
        query.setPageSize(20);

        List<ProblemVO> records = problemService.pageProblems(query).getRecords();

        assertTrue(records.size() > 1);
        for (int i = 1; i < records.size(); i++) {
            assertTrue(records.get(i - 1).getCode() <= records.get(i).getCode(),
                    "题号未按从小到大排序: " + records.get(i - 1).getCode() + " > " + records.get(i).getCode());
        }
    }

    @Test
    @DisplayName("客服按题号只返回已发布题目及受限题面概览")
    void assistantSearchesPublishedProblemByCode() {
        AssistantProblemQueryDTO query = new AssistantProblemQueryDTO();
        query.setMode(AssistantProblemQueryMode.SEARCH);
        query.setCode(1);
        query.setIncludeOverview(true);
        query.setLimit(5);

        var result = problemService.queryForAssistant(query);

        assertEquals("CODE", result.getMatchType());
        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getItems().get(0).getCode());
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

        assertEquals(List.of(7, 1), result.getItems().stream()
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

        assertEquals(List.of(6, 7, 8), result.getItems().stream()
                .map(item -> item.getCode()).toList());
        assertEquals(3, result.getItems().size());
        assertTrue(result.getTruncated());
        assertTrue(result.getItems().stream().noneMatch(item -> item.getCode() == 9));
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
            case "code" -> BigDecimal.valueOf(problem.getCode());
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
