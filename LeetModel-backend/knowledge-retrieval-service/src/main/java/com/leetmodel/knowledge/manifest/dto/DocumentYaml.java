package com.leetmodel.knowledge.manifest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录下属原子 Markdown 文档 YAML 映射实体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentYaml {

    @JsonProperty("file")
    private String file;

    @JsonProperty("title")
    private String title;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("doc_tags")
    private List<String> docTags = new ArrayList<>();

    @JsonProperty("methods")
    private List<String> methods = new ArrayList<>();

    @JsonProperty("authority_level")
    private String authorityLevel;

    @JsonProperty("estimated_tokens")
    private Integer estimatedTokens;
}
