package com.leetmodel.knowledge.defense.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 智能选拔模型结构化输出响应体。
 * 兼容 selectedPaths 与历史 paths 字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSelectionResponse(
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("selectedPaths") List<String> selectedPaths,
        @JsonProperty("paths") List<String> paths) {

    public CatalogSelectionResponse(String reasoning, List<String> selectedPaths) {
        this(reasoning, selectedPaths, List.of());
    }

    public CatalogSelectionResponse {
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            selectedPaths = (paths != null) ? paths : List.of();
        }
    }
}
