package com.leetmodel.aigateway.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * OpenAI 兼容模型列表响应。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiCompatibleModelList(List<Model> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Model(String id, String ownedBy) {
    }
}
