package com.leetmodel.problem.search;

import java.util.List;

/**
 * 全文检索命中的题目分页结果。
 *
 * @param problemIds 当前页题目标识，按相关度排序
 * @param total 命中总数
 */
public record ProblemSearchPage(List<Long> problemIds, long total) {
}
