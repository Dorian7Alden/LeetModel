package com.leetmodel.suggestion.workflow;

import java.util.List;

/**
 * 首版论文改善建议的稳定结构化输出。
 */
public record SuggestionV1Output(String summary, List<Item> items) {

    public record Item(String priority, String category, String title,
                       String action, String evidence, Integer page) {
    }
}
