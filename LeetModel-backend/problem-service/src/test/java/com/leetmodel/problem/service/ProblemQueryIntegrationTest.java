package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.vo.ProblemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
