package com.leetmodel.knowledge.manifest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录级继承标签 YAML 映射结构体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DirectoryTagsYaml {

    @JsonProperty("contest")
    private String contest;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("problem")
    private String problem;

    @JsonProperty("prize")
    private String prize;

    @JsonProperty("problem_type")
    private String problemType;

    @JsonProperty("methods")
    private List<String> methods = new ArrayList<>();

    @JsonProperty("authority_level")
    private String authorityLevel;
}
