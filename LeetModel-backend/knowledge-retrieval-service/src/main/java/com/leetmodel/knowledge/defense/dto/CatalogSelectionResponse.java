package com.leetmodel.knowledge.defense.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * AI 智能选拔模型结构化输出响应体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogSelectionResponse(
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("selectedPaths") List<String> selectedPaths) {

    public CatalogSelectionResponse {
        if (selectedPaths == null) selectedPaths = List.of();
    }
}
