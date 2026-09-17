package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 客服题目查询结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProblemResultDTO {
    private List<Item> items;
    private String matchType;
    private Boolean truncated;
    private List<String> matchedConditions;

    /**
     * 对模型公开的最小已发布题目事实。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Integer code;
        private String title;
        private String contestCode;
        private String contestName;
        private Integer year;
        private String statementLanguage;
        private Integer difficulty;
        private Integer durationMinutes;
        private List<String> tagNames;
        private String overview;
    }
}
