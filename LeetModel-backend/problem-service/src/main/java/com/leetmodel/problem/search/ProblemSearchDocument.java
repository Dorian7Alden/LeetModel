package com.leetmodel.problem.search;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题库全文检索文档。
 *
 * @param problemId 题目标识
 * @param code 题目编号
 * @param problemNumber 赛事题号
 * @param title 题目标题
 * @param content 题面 Markdown 原文
 * @param contestId 赛事标识
 * @param year 年份
 * @param difficulty 难度
 * @param statementLanguage 题面语言
 * @param tagIds 标签标识列表
 * @param status 发布状态
 * @param updateTime 题目最近更新时间
 */
public record ProblemSearchDocument(
        Long problemId,
        Integer code,
        String problemNumber,
        String title,
        String content,
        Long contestId,
        Integer year,
        Integer difficulty,
        String statementLanguage,
        List<Long> tagIds,
        Integer status,
        LocalDateTime updateTime
) {
}
